package situatedInference


import com.ontotext.trree.AbstractInferencerTask
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.StatementIdIterator.*
import com.ontotext.trree.plugin.provenance.MemoryStorage
import com.ontotext.trree.plugin.provenance.Storage
import com.ontotext.trree.sdk.Entities.Type.LITERAL
import com.ontotext.trree.sdk.Entities.Type.URI
import com.ontotext.trree.sdk.StatementIterator
import org.slf4j.LoggerFactory

//FIXME there is no reason for this to be an iterator

class SituationIter(
    private val requestContext: SituatedInferenceContext,
    private val situationId: Long,
    private val situatesId: Long,
    private val boundContextId: Long, //FIXME this is useless
    private val sourceStatements: StatementIterator
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
        while (sourceStatements.next()) {
            doForwardChaining(
                sourceStatements.subject,
                sourceStatements.predicate,
                sourceStatements.`object`,
                sourceStatements.context
            )
        }
    }

    private fun doForwardChaining(subject: Long, predicate: Long, `object`: Long, context: Long) {
        storage.add(subject, predicate, `object`, context, 2) //TODO possibly should be implicit status
        val storageIterator = storage.bottom()
        while (storageIterator.hasNext()) {
            if (inferencer.inferStatementsFlag) {
                logger.debug("Running inference with {} {} {}", subject, predicate, `object`)
                inferencer.doInference(
                    storageIterator.subj,
                    storageIterator.pred,
                    storageIterator.context,
                    if (storageIterator.status and SYSTEM_STATEMENT_STATUS != 0) storageIterator.context else 0,
                    0,
                    this
                ) // this will call back ruleFired which is overridden below
            } else {
                logger.warn("Inference is not enabled - skipping inference")
            }
            storageIterator.next()
        }
        storageIterator.close()
    }

    override fun next(): Boolean {
        return false
    }

    override fun close() {
//        result.close()
        sourceStatements.close()
        storage.clear()
        requestContext.situations.remove(situationId)
    }


    //each inferred statement is passed to this function
    override fun ruleFired(subject: Long, predicate: Long, `object`: Long, context: Long, status: Int, p5: Int) {
        val entitiesAdapter = repositoryConnection.entityPoolConnection.entities
        if (entitiesAdapter.getType(subject) != LITERAL && entitiesAdapter.getType(predicate) == URI) {
            if (statementIsAxiom(subject, predicate, `object`)) {
                logger.trace(
                    "Rule fired but statement already existing as axiom {} {} {}",
                    subject,
                    predicate,
                    `object`,
                )
            } else {
                logger.trace(
                    "Adding inferred statement {} {} {}",
                    subject,
                    predicate,
                    `object`,
                )
                storage.add(subject, predicate, `object`, context, status)
            }
        }
    }

    private fun statementIsAxiom(subject: Long, predicate: Long, `object`: Long): Boolean {
        repositoryConnection.getStatements(
            subject, predicate, `object`, DELETED_STATEMENT_STATUS or SKIP_ON_BROWSE_STATEMENT_STATUS
        ).use {
            while (it.hasNext()) {
                if (it.status and AXIOM_STATEMENT_STATUS != 0) {
                    return true
                }
                it.next()
            }
        }
        return false
    }

    override fun doInference(p0: Long, p1: Long, p2: Long, p3: Long, p4: Int, p5: AbstractInferencerTask?) {
        throw RuntimeException("Not implemented, you shouldn't be here")
    }

    override fun getRepStatements(p0: Long, p1: Long, p2: Long, p3: Int): StatementIdIterator {
        throw RuntimeException("Not implemented, you shouldn't be here")
    }

    override fun getRepStatements(p0: Long, p1: Long, p2: Long, p3: Long, p4: Int): StatementIdIterator {
        throw RuntimeException("Not implemented, you shouldn't be here")
    }


}