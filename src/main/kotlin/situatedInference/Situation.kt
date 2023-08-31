package situatedInference


import com.ontotext.trree.AbstractInferencerTask
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.StatementIdIterator.*
import com.ontotext.trree.SwitchableInferencer
import com.ontotext.trree.sdk.Entities.Type.LITERAL
import com.ontotext.trree.sdk.Entities.Type.URI
import com.ontotext.trree.sdk.Entities.UNBOUND
import com.ontotext.trree.sdk.PluginException
import org.slf4j.LoggerFactory


class Situation(
    val id: Long,
    private val boundContexts: Set<Long>,
    private val requestContext: SituatedInferenceContext,
) : ContextWithStorage(), AbstractInferencerTask {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val inferencer = requestContext.inferencer
    private val repositoryConnection = requestContext.repositoryConnection

    private var isUpdated = false

    var baseContext: Long? = null
        private set


    fun refreshAndFind(subjectId: Long, predicateId: Long, objectId: Long, contextId: Long = 0): Sequence<Quad> {
        if (!isUpdated) {
            refresh()
            isUpdated = true
        }
        logger.debug("total size in find {}", storage.size())
        return find(subjectId, predicateId, objectId, contextId)
    }

    fun materialize() {
//        repositoryConnection.transaction {
//            storage.find(0, 0, 0, 0).asSequence().filter { !it.isExplicit() }.forEach { stmt ->
//                it.removeStatements(
//                    stmt.subject,
//                    stmt.predicate,
//                    stmt.`object`
//                )
//            }
//        }
//
//        refresh()

        repositoryConnection.transaction {
            storage.find(0, 0, 0, 0).asSequence().forEach { stmt ->
                it.putStatement(
                    stmt.subject,
                    stmt.predicate,
                    stmt.`object`,
                    stmt.context,
                    stmt.status
                )
            }
        }
    }

    fun refresh() {
        storage.clear()
        getStatementsToSituate().forEach {
            inferClosureAndAddToStorage(it.subject, it.predicate, it.`object`, it.context)
        }
    }

    private fun getStatementsToSituate(): Sequence<Quad> =
        boundContexts.asSequence().map(::replaceDefaultGraphId).map { contextInScope ->
            requestContext.inMemoryContexts[contextInScope]?.getAll()
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
        storage.add(subject, predicate, `object`, context, EXPLICIT_STATEMENT_STATUS)
        logger.debug("forward chaining added ${getPrettyStringFor(subject, predicate, `object`)}")

        if (!requestContext.isInferenceEnabled) {
            logger.warn("Inference is not enabled - skipping inference")
            return
        }

        storageIterator.asSequence().forEach {
            if (inferencer.hasConsistencyRules()) {
                logger.debug("ruleset has consistency rules")
                val currentInferencer = (inferencer as SwitchableInferencer).currentInferencer
                val inconsistencies = currentInferencer.checkForInconsistencies(
                    repositoryConnection.entityPoolConnection,
                    it.subject,
                    it.predicate,
                    it.`object`,
                    it.context,
                    it.status
                )
                if (inconsistencies.isNotBlank()) logger.debug("inconsistencies {}", inconsistencies)
            }
            logger.debug("Running inference with {}", getPrettyStringFor(it.subject, it.predicate, it.`object`))

            inferencer.doInference(
                it.subject,
                it.predicate,
                it.`object`,
                if (it.isSystemStatement()) it.context else 0,
                0,      //infer with all rules
                this    //call back overridden task methods
            ) // this will call back ruleFired which is overridden below
        }
    }


    //each inferred statement is passed to this function as call back from inferencer.doInference
    override fun ruleFired(
        subject: Long,
        predicate: Long,
        `object`: Long,
        context: Long,
        status: Int,
        p5: Int
    ) {
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
                "Rule fired but statement already existing as axiom ${getPrettyStringFor(subject, predicate, `object`)}"
            )
            return
        }

        storage.add(subject, predicate, `object`, context, status)
        logger.debug(
            "Rule fired and adding inferred statement ${
                getPrettyStringFor(subject, predicate, `object`)
            }" + " Total size ${storage.size()}"
        )
    }

    private fun statementIsAxiom(subject: Long, predicate: Long, `object`: Long): Boolean {
        repositoryConnection.getStatements(
            subject,
            predicate,
            `object`,
            DELETED_STATEMENT_STATUS or SKIP_ON_BROWSE_STATEMENT_STATUS or GENERATED_STATEMENT_STATUS
        ).use { iter ->
            return iter.asSequence().any { it.isAxiom() }
        }
    }

    override fun getRepStatements(
        subject: Long,
        predicate: Long,
        `object`: Long,
        status: Int
    ): StatementIdIterator {
        logger.debug("gettingRepStatements for ${getPrettyStringFor(subject, predicate, `object`)}")
        val axiomsFromRepo =
            repositoryConnection.getStatements(subject, predicate, `object`, status).asSequence()
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
        logger.debug("gettingRepStatements for ${getPrettyStringFor(subject, predicate, `object`)}")
        val axiomsFromRepo =
            repositoryConnection.getStatements(subject, predicate, `object`, context, status).asSequence()
                .filter { it.isAxiom() }
        val statementsFromStorage = storage.find(subject, predicate, `object`, context).asSequence()
        val sharedStatements =
            repositoryConnection.getStatements(subject, predicate, `object`, requestContext.sharedScope, status)
                .asSequence()
        return statementIdIteratorFromSequence(axiomsFromRepo + statementsFromStorage + sharedStatements)
    }

    private fun getPrettyStringFor(
        subject: Long,
        predicate: Long,
        `object`: Long,
        context: Long? = null
    ) = buildString {
        append("($subject $predicate $`object` $context):")
        append("(")
        append(getPrettyStringValue(subject) + " ")
        append(getPrettyStringValue(predicate) + " ")
        append(getPrettyStringValue(`object`) + " ")
        append(getPrettyStringValue(context))
        append(")")
    }


    private fun getPrettyStringValue(entityId: Long?): String {
        return when (entityId) {
            null -> "null"
            0L -> "unbound"
            else -> try {
                repositoryConnection.entityPoolConnection.entities[entityId].stringValue()
            } catch (e: Exception) {
                "no entity found"
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

