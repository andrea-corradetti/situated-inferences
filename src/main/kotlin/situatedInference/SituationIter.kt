package situatedInference


import com.ontotext.trree.AbstractInferencerTask
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.StatementIdIterator.*
import com.ontotext.trree.plugin.provenance.MemoryStorage
import com.ontotext.trree.plugin.provenance.Storage
import com.ontotext.trree.sdk.Entities.Type.LITERAL
import com.ontotext.trree.sdk.Entities.Type.URI
import com.ontotext.trree.sdk.PluginException
import com.ontotext.trree.sdk.StatementIterator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

//FIXME there is no reason for this to be an iterator

class SituationIter(
    private val requestContext: SituatedInferenceContext,
    private val situationId: Long,
    private val situatesId: Long,
    private val boundContextId: Long, //FIXME this is useless
    private val sourceStatements: Sequence<Quad>
) : StatementIterator(), AbstractInferencerTask {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val storage: Storage = MemoryStorage()
    private val inferencer = requestContext.inferencer
    private val repositoryConnection = requestContext.repositoryConnection

    init {
        subject = situationId
        predicate = situatesId
        `object` = boundContextId
    }

    fun getStatementsIterator(): StatementIdIterator {
        return storage.bottom()
    }

    fun inferImplicitStatements() {
        sourceStatements.forEach {
            doForwardChaining(it.subject, it.predicate, it.`object`, it.context)
        }
    }

    private fun doForwardChaining(subject: Long, predicate: Long, `object`: Long, context: Long) {
        val storageIterator = storage.bottom()
        storage.add(
            subject, predicate, `object`, context, EXPLICIT_STATEMENT_STATUS
        ) //TODO possibly should be implicit status
        logger.debug("forward chaining added ${getPrettyStringForTriple(subject, predicate, `object`)}")
        while (storageIterator.hasNext()) {
            if (inferencer.inferStatementsFlag) {
                logger.debug(
                    """Running inference with {} {} {}
                        | values : {} {} {}
                    """.trimMargin(),
                    storageIterator.subj,
                    storageIterator.pred,
                    storageIterator.obj,
                    getStringValue(storageIterator.subj),
                    getStringValue(storageIterator.pred),
                    getStringValue(storageIterator.obj)
                )

                inferencer.doInference(
                    storageIterator.subj,
                    storageIterator.pred,
                    storageIterator.obj,
                    if (storageIterator.status and SYSTEM_STATEMENT_STATUS != 0) storageIterator.context else 0,
                    0,      //infer with all rules
                    this    //call back overridden methods in this class
                ) // this will call back ruleFired which is overridden below
            } else {
                logger.warn("Inference is not enabled - skipping inference")
            }
            storageIterator.next()
        }
        storageIterator.close()
    }

    private fun getStringValue(entityId: Long?): String {
        if (entityId == null) return "null"
        if (entityId == 0L) return "unbound"
        return try {
            requestContext.repositoryConnection.entityPoolConnection.entities[entityId].stringValue()
        } catch (e: Exception) {
            logger.debug(e.message)
            "invalid id"
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
                    getPrettyStringForTriple(
                        subject, predicate, `object`
                    )
                }"
            )
        } else {
            storage.add(subject, predicate, `object`, context, status)
            logger.debug(
                "Rule fired and adding inferred statement ${getPrettyStringForTriple(subject, predicate, `object`)}"
            )
        }
    }

    override fun next(): Boolean {
        return false
    }


    override fun close() { //        result.close()
        storage.clear()
        requestContext.situations.remove(situationId)
    }

    private fun statementIsAxiom(subject: Long, predicate: Long, `object`: Long): Boolean {
        repositoryConnection.getStatements(
            subject, predicate, `object`, DELETED_STATEMENT_STATUS or SKIP_ON_BROWSE_STATEMENT_STATUS
        ).use { iter ->
            return iter.asSequence().any { it.isAxiom() }
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

    override fun getRepStatements(subject: Long, predicate: Long, `object`: Long, status: Int): StatementIdIterator {
        logger.debug("gettingRepStatements for ${getPrettyStringForTriple(subject, predicate, `object`)}")
        val statementsFromRepo = repositoryConnection.getStatements(subject, predicate, `object`, status)
        val statementsFromStorage = storage.find(subject, predicate, `object`)
        val statementsSequence = statementsFromRepo.asSequence() + statementsFromStorage.asSequence()
        return statementIdIteratorFromSequence(statementsSequence.filter { logger.logStatementIfInvalid(it); it.isAxiom() })
    }

    override fun getRepStatements(
        subject: Long, predicate: Long, `object`: Long, context: Long, status: Int
    ): StatementIdIterator {
        logger.debug("gettingRepStatements for ${getPrettyStringForTriple(subject, predicate, `object`)}")
        val statementsFromRepo = repositoryConnection.getStatements(subject, predicate, `object`, context, status)
        val statementsFromStorage = storage.find(subject, predicate, `object`, context)
        val statementsSequence = statementsFromRepo.asSequence() + statementsFromStorage.asSequence()
        return statementIdIteratorFromSequence(statementsSequence.filter { logger.logStatementIfInvalid(it); it.isAxiom() })
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

fun sequenceFromStatementIdIterators(vararg iterators: StatementIdIterator): Sequence<Quad> = sequence {
    iterators.forEach {
        while (it.hasNext()) {
            yield(Quad(it.subj, it.pred, it.obj, it.context, it.status))
            it.next()
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
