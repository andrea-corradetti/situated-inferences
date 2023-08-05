package situatedInference


import com.ontotext.trree.AbstractInferencerTask
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.StatementIdIterator.*
import com.ontotext.trree.plugin.provenance.MemoryStorage
import com.ontotext.trree.plugin.provenance.Storage
import com.ontotext.trree.sdk.Entities.Type.LITERAL
import com.ontotext.trree.sdk.Entities.Type.URI
import com.ontotext.trree.sdk.PluginException
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class Situation(
    private val requestContext: SituatedInferenceContext,
    private val situationId: Long,
    private val situatesId: Long,
    private val boundContexts: Set<Long>,
    private val sourceStatements: Sequence<Quad>
) : AbstractInferencerTask {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val storage: Storage = MemoryStorage()
    private val inferencer = requestContext.inferencer
    private val repositoryConnection = requestContext.repositoryConnection

    fun getBottomIterator(): StatementIdIterator {
        return storage.bottom()
    }

    fun inferImplicitStatements() {
        sourceStatements.forEach {
            doForwardChaining(it.subject, it.predicate, it.`object`, it.context)
        }
    }


    //FIXME same triples with different contexts are duplicated in storage
    private fun doForwardChaining(subject: Long, predicate: Long, `object`: Long, context: Long) {
        val storageIterator = storage.bottom()
        storage.add(
            subject, predicate, `object`, context, EXPLICIT_STATEMENT_STATUS
        ) //TODO possibly should be implicit status
        logger.debug("forward chaining added ${getPrettyStringForTriple(subject, predicate, `object`)}")
        storageIterator.asSequence().forEach {
            if (inferencer.inferStatementsFlag) {
                logger.debug(
                    """Running inference with {} {} {}
                        | values : {} {} {}
                    """.trimMargin(),
                    it.subject,
                    it.predicate,
                    it.`object`,
                    getStringValue(it.subject),
                    getStringValue(it.predicate),
                    getStringValue(it.`object`)
                )

                inferencer.doInference(
                    it.subject,
                    it.predicate,
                    it.`object`,
                    if (it.isSystemStatement()) it.context else 0,
                    0,      //infer with all rules
                    this    //call back overridden methods in this class
                ) // this will call back ruleFired which is overridden below
            } else {
                logger.warn("Inference is not enabled - skipping inference")
            }
        }
    }

    private fun getStringValue(entityId: Long?): String {
        return when (entityId) {
            null -> "null"
            0L -> "unbound"
            else -> try {
                requestContext.repositoryConnection.entityPoolConnection.entities[entityId].stringValue()
            } catch (e: Exception) {
                logger.debug(e.message)
                "invalid id"
            }
        }
    }

    private fun getPrettyStringForTriple(subject: Long, predicate: Long, `object`: Long): String = """
        $subject $predicate $`object` : <${getStringValue(subject)} ${getStringValue(predicate)} ${
        getStringValue(
            `object`
        )
    }>
    """.trimIndent()

    //each inferred statement is passed to this function as call back from inferencer.doInference
    override fun ruleFired(subject: Long, predicate: Long, `object`: Long, context: Long, status: Int, p5: Int) {
        val entitiesAdapter = repositoryConnection.entityPoolConnection.entities
        if (entitiesAdapter.getType(subject) == LITERAL || entitiesAdapter.getType(predicate) != URI) {
            logger.warn("Rule fired but skipping because triple isn't formed correctly")
            return
        }
        if (subject == 0L || predicate == 0L || `object` == 0L) {
            throw PluginException("Generated incorrect statement $subject $predicate $`object`")
        }

        if (statementIsAxiom(subject, predicate, `object`)) {
            logger.debug(
                "Rule fired but statement already existing as axiom ${
                    getPrettyStringForTriple(subject, predicate, `object`)
                }"
            )
        }
//        else if (storage.contains(subject, predicate, `object`, context)) {
//            logger.debug(
//                "Rule fired but statement already already in storage ${
//                    getPrettyStringForTriple(subject, predicate, `object`)
//                }"
//            )
//        }
        else {
            storage.add(subject, predicate, `object`, context, status)
            logger.debug(
                "Rule fired and adding inferred statement ${getPrettyStringForTriple(subject, predicate, `object`)}"
            )
        }
    }

    private fun statementIsAxiom(subject: Long, predicate: Long, `object`: Long): Boolean {
        repositoryConnection.getStatements(
            subject, predicate, `object`, DELETED_STATEMENT_STATUS or SKIP_ON_BROWSE_STATEMENT_STATUS
        ).use { iter ->
            return iter.asSequence().any { it.isAxiom() }
        }
    }

    private fun Storage.contains(subject: Long, predicate: Long, `object`: Long, context: Long): Boolean =
        this.find(subject, predicate, `object`, context).asSequence().any()

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

    override fun getRepStatements(subject: Long, predicate: Long, `object`: Long, status: Int): StatementIdIterator {
        logger.debug("gettingRepStatements for ${getPrettyStringForTriple(subject, predicate, `object`)}")
        val statementsFromRepo = repositoryConnection.getStatements(subject, predicate, `object`, status)
        val statementsFromStorage = storage.find(subject, predicate, `object`)
        val statementsSequence = statementsFromRepo.asSequence() + statementsFromStorage.asSequence()
        return statementIdIteratorFromSequence(statementsSequence) //TODO this works as intended even though I'm not filtering
    }

    override fun getRepStatements(
        subject: Long, predicate: Long, `object`: Long, context: Long, status: Int
    ): StatementIdIterator {
        logger.debug("gettingRepStatements for ${getPrettyStringForTriple(subject, predicate, `object`)}")
        val statementsFromRepo = repositoryConnection.getStatements(subject, predicate, `object`, context, status)
        val statementsFromStorage = storage.find(subject, predicate, `object`, context)
        val statementsSequence = statementsFromRepo.asSequence() + statementsFromStorage.asSequence()
        return statementIdIteratorFromSequence(statementsSequence)
    }


    fun find(subjectId: Long, predicateId: Long, objectId: Long, contextId: Long? = null): StatementIdIterator {
        return if (contextId == null) {
            storage.find(subjectId, predicateId, objectId)
        } else {
            storage.find(subjectId, predicateId, objectId, contextId)
        } ?: empty
    }

    companion object {
        fun Logger.logStatementIfInvalid(it: Quad) {
            if (it.subject == 0L || it.predicate == 0L || it.`object` == 0L) {
                debug("sequence from iterator returned invalid statement {}", it)
            }
        }
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
