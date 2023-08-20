package situatedInference


import com.ontotext.trree.AbstractInferencerTask
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.StatementIdIterator.*
import com.ontotext.trree.plugin.provenance.MemoryStorage
import com.ontotext.trree.plugin.provenance.Storage
import com.ontotext.trree.sdk.Entities.Type.LITERAL
import com.ontotext.trree.sdk.Entities.Type.URI
import com.ontotext.trree.sdk.Entities.UNBOUND
import com.ontotext.trree.sdk.PluginException
import org.slf4j.LoggerFactory


class Situation(
    private val requestContext: SituatedInferenceContext,
    private val situationId: Long,
    private val boundContexts: Set<Long>,
) : AbstractInferencerTask {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val storage: Storage = MemoryStorage()
    private val inferencer = requestContext.inferencer
    private val repositoryConnection = requestContext.repositoryConnection

    private var isUpdated = false

    fun find(subjectId: Long, predicateId: Long, objectId: Long, contextId: Long? = null): StatementIdIterator {
        if (!isUpdated) {
            refresh()
            isUpdated = true
        }
        logger.debug("total size in find {}", storage.size())
        return if (contextId == null) {
            storage.find(subjectId, predicateId, objectId, 0)
        } else {
            storage.find(subjectId, predicateId, objectId, contextId)
        } ?: empty
    }

    private fun refresh() {
        storage.clear()
        getStatementsToSituate().forEach {
            inferClosureAndAddToStorage(it.subject, it.predicate, it.`object`, it.context)
        }
    }

    private fun getStatementsToSituate(): Sequence<Quad> =
        boundContexts.asSequence().map(::replaceDefaultGraphId).map { contextInScope ->
            requestContext.situations[contextInScope]?.getAll()?.asSequence()
                ?: requestContext.repositoryConnection.getStatements(
                    UNBOUND,
                    UNBOUND,
                    UNBOUND,
                    contextInScope,
                    excludeDeletedHiddenInferred
                ).asSequence()
        }.flatten()


    //FIXME same triples with different contexts are duplicated in storage (this might be good for inconsistencies)
    private fun inferClosureAndAddToStorage(subject: Long, predicate: Long, `object`: Long, context: Long) {
        val storageIterator = storage.bottom()
        storage.add(
            subject, predicate, `object`, context, EXPLICIT_STATEMENT_STATUS
        ) //TODO possibly should be implicit status
        logger.debug("forward chaining added ${getPrettyStringForTriple(subject, predicate, `object`)}")

        storageIterator.asSequence().forEach {
            if (inferencer.hasConsistencyRules()) {
                logger.debug("ruleset has consistency rules")
            }
            if (inferencer.inferStatementsFlag) {
                logger.debug(
                    """Running inference with {} {} {}
                        | values : {} {} {}
                    """.trimMargin(),
                    it.subject,
                    it.predicate,
                    it.`object`,
                    requestContext.getStringValue(it.subject),
                    requestContext.getStringValue(it.predicate),
                    requestContext.getStringValue(it.`object`)
                )


                inferencer.doInference(
                    it.subject,
                    it.predicate,
                    it.`object`,
                    if (it.isSystemStatement()) it.context else 0,
                    0,      //infer with all rules
                    this    //call back overridden task methods
                ) // this will call back ruleFired which is overridden below
            } else {
                logger.warn("Inference is not enabled - skipping inference")
            }
        }
    }


    //each inferred statement is passed to this function as call back from inferencer.doInference
    override fun ruleFired(subject: Long, predicate: Long, `object`: Long, context: Long, status: Int, p5: Int) {
        val entitiesAdapter = repositoryConnection.entityPoolConnection.entities
        if (entitiesAdapter.getType(subject) == LITERAL || entitiesAdapter.getType(predicate) != URI) {
            logger.warn("Rule fired but skipping because triple isn't formed correctly")
            return
        }
        if (subject == 0L || predicate == 0L || `object` == 0L) {
            throw PluginException("Generated incorrect statement $subject $predicate $`object`. Check if you are running inferences on statements with values of 0")
        }

        if (statementIsAxiom(subject, predicate, `object`)) {
            logger.debug(
                "Rule fired but statement already existing as axiom ${
                    getPrettyStringForTriple(subject, predicate, `object`)
                }"
            )
        } else {
            storage.add(subject, predicate, `object`, context, status)
            logger.debug(
                "Rule fired and adding inferred statement ${getPrettyStringForTriple(subject, predicate, `object`)}" +
                " Total size ${storage.size()}"
            )
        }
    }

    fun getBottomIterator(): StatementIdIterator {
        return storage.bottom()
    }

    private fun getPrettyStringForTriple(subject: Long, predicate: Long, `object`: Long): String = """
        $subject $predicate $`object` : <${requestContext.getStringValue(subject)} ${
        requestContext.getStringValue(
            predicate
        )
    } ${
        requestContext.getStringValue(
            `object`
        )
    }>
    """.trimIndent()

    private fun statementIsAxiom(subject: Long, predicate: Long, `object`: Long): Boolean {
        repositoryConnection.getStatements(
            subject, predicate, `object`, DELETED_STATEMENT_STATUS or SKIP_ON_BROWSE_STATEMENT_STATUS
        ).use { iter ->
            return iter.asSequence().any { it.isAxiom() }
        }
    }

    private fun Storage.contains(subject: Long, predicate: Long, `object`: Long, context: Long): Boolean =
        this.find(subject, predicate, `object`, context).asSequence().any()

    override fun getRepStatements(subject: Long, predicate: Long, `object`: Long, status: Int): StatementIdIterator {
        logger.debug("gettingRepStatements for ${getPrettyStringForTriple(subject, predicate, `object`)}")
        val axiomsFromRepo = repositoryConnection.getStatements(subject, predicate, `object`, status).asSequence()
            .filter { it.isAxiom() }
        val statementsFromStorage = storage.find(subject, predicate, `object`).asSequence()
        val sharedStatements =
            repositoryConnection.getStatements(subject, predicate, `object`, requestContext.sharedScope, status)
                .asSequence()
        return statementIdIteratorFromSequence(axiomsFromRepo + statementsFromStorage + sharedStatements)
    }

    override fun getRepStatements(
        subject: Long, predicate: Long, `object`: Long, context: Long, status: Int
    ): StatementIdIterator {
        logger.debug("gettingRepStatements for ${getPrettyStringForTriple(subject, predicate, `object`)}")
        val axiomsFromRepo =
            repositoryConnection.getStatements(subject, predicate, `object`, context, status).asSequence()
                .filter { it.isAxiom() }
        val statementsFromStorage = storage.find(subject, predicate, `object`, context).asSequence()
        val sharedStatements =
            repositoryConnection.getStatements(subject, predicate, `object`, requestContext.sharedScope, status)
                .asSequence()
        return statementIdIteratorFromSequence(axiomsFromRepo + statementsFromStorage + sharedStatements)
    }


    fun getAll(): StatementIdIterator = find(UNBOUND, UNBOUND, UNBOUND, UNBOUND)


    private fun SituatedInferenceContext.getStringValue(entityId: Long?): String {
        return when (entityId) {
            null -> "null"
            0L -> "unbound"
            else -> try {
                repositoryConnection.entityPoolConnection.entities[entityId].stringValue()
            } catch (e: Exception) {
                "invalid id"
            }
        }
    }


    override fun doInference(
        subject: Long,
        predicate: Long,
        `object`: Long,
        context: Long,
        status: Int,
        taskInferencer: AbstractInferencerTask?
    ) {
        throw PluginException("Not implemented for Situated-Inferences Plugin")
    }
}

fun statementIdIteratorFromSequence(statements: Sequence<Quad>): StatementIdIterator {
    return object : StatementIdIterator() {
        val iterator = statements.iterator()

        init {
            next()
        }

        override fun next() {
            if (iterator.hasNext()) {
                val quad = iterator.next()
                subj = quad.subject
                pred = quad.predicate
                obj = quad.`object`
                context = quad.context
                status = quad.status
                found = true
            } else {
                found = false
            }
        }

        override fun close() {}
        override fun changeStatus(p0: Int) {}
    }

}