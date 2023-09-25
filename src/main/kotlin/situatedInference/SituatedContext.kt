package situatedInference

import com.ontotext.trree.*
import com.ontotext.trree.consistency.ConsistencyException
import com.ontotext.trree.sdk.Entities
import com.ontotext.trree.sdk.Entities.UNBOUND
import com.ontotext.trree.sdk.PluginException
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.slf4j.LoggerFactory


class SituatedContext(
    var situatedContextId: Long,
    override val sourceId: Long,
    private val mainContextId: Long? = null,
    private val additionalContexts: Set<Long> = emptySet(),
    private val requestContext: SituatedInferenceContext
) : ContextWithStorage(),
    Quotable by QuotableImpl(sourceId, situatedContextId, requestContext),
    AbstractInferencerTask,
    CheckableForConsistency {

    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val inferencer = requestContext.inferencer
    private val repositoryConnection = requestContext.repositoryConnection

//    private fun notifyTasks() {
//        requestContext.schemas.values.filter { situatedContextId in it.contextsToSituate + it.sharedContexts }.forEach {
//            it.boundTasks.forEach { task -> task.alreadySituated -= situatedContextId }
//        }
//    }


//    private val sourceContextId: Long
//        get() = requestContext.statementIdToSingletonId[sourceId] ?: sourceId


    fun reset() {
        storage.clear()
        getStatementsToSituate().forEach {
            inferLocally(it.subject, it.predicate, it.`object`, it.context)
        }
//        notifyTasks()
    }

    fun computeClosure() {
        getAll().forEach { inferLocally(it.subject, it.predicate, it.`object`, it.context) }
    }

    //TODO fix nullable
    private fun getStatementsToSituate(): Sequence<Quad> =
        (additionalContexts + mainContextId!!).asSequence().map(::replaceDefaultGraphId).map { contextInScope ->
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
    fun inferLocally(subject: Long, predicate: Long, `object`: Long, context: Long) {
        val storageIterator = storage.bottom()
        storage.add(subject, predicate, `object`, situatedContextId, StatementIdIterator.EXPLICIT_STATEMENT_STATUS)
//        storage.add(subject, predicate, `object`, context, StatementIdIterator.EXPLICIT_STATEMENT_STATUS)
//        logger.debug(
//            "forward chaining added ${
//                requestContext.repositoryConnection.getPrettyStringFor(
//                    subject,
//                    predicate,
//                    `object`
//                )
//            }"
//        )

        if (!requestContext.isInferenceEnabled) {
            logger.warn("Inference is not enabled - skipping inference")
            return
        }

        storageIterator.asSequence().forEach {
            if (inferencer.hasConsistencyRules()) {
                //logger.debug("ruleset has consistency rules")
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
//            logger.debug("Running inference with {}", getPrettyStringFor(it.subject, it.predicate, it.`object`))

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
        if (entitiesAdapter.getType(subject) == Entities.Type.LITERAL || entitiesAdapter.getType(predicate) != Entities.Type.URI) {
            logger.warn("Rule fired but skipping because triple isn't formed correctly")
            return
        }
        if (subject == 0L || predicate == 0L || `object` == 0L) {
            throw PluginException("Generated incorrect statement $subject $predicate $`object`. Check if you are running inferences on statements with values of 0")
        }

        if (statementIsAxiom(subject, predicate, `object`)) {
            logger.debug(
                "Rule fired but statement already existing as axiom ${
                    requestContext.repositoryConnection.getPrettyStringFor(
                        subject,
                        predicate,
                        `object`
                    )
                }"
            )
            return
        }

        storage.add(subject, predicate, `object`, situatedContextId, status)
//        storage.add(subject, predicate, `object`, context, status)
//        logger.debug(
//            "Rule fired and adding inferred statement ${
//                getPrettyStringFor(subject, predicate, `object`)
//            }" + " Total size ${storage.size()}"
//        )
    }

    private fun statementIsAxiom(subject: Long, predicate: Long, `object`: Long): Boolean {
        return repositoryConnection.getStatements(
            subject,
            predicate,
            `object`,
            StatementIdIterator.DELETED_STATEMENT_STATUS or StatementIdIterator.SKIP_ON_BROWSE_STATEMENT_STATUS or StatementIdIterator.GENERATED_STATEMENT_STATUS
        ).asSequence().any { it.isAxiom() }
    }

    override fun getRepStatements(
        subject: Long,
        predicate: Long,
        `object`: Long,
        status: Int
    ): StatementIdIterator {
//        logger.debug("gettingRepStatements for ${getPrettyStringFor(subject, predicate, `object`)}")
        val axiomsFromRepo =
            repositoryConnection.getStatements(subject, predicate, `object`, status).asSequence()
                .filter { it.isAxiom() }
        val statementsFromStorage = storage.find(subject, predicate, `object`).asSequence()

        return statementIdIteratorFromSequence(axiomsFromRepo + statementsFromStorage)
    }

    override fun getRepStatements(
        subject: Long, predicate: Long, `object`: Long, context: Long, status: Int
    ): StatementIdIterator {
//        logger.debug("gettingRepStatements for ${getPrettyStringFor(subject, predicate, `object`)}")
        val axiomsFromRepo =
            repositoryConnection.getStatements(subject, predicate, `object`, context, status).asSequence()
                .filter { it.isAxiom() }
        val statementsFromStorage = storage.find(subject, predicate, `object`, context).asSequence()
        return statementIdIteratorFromSequence(axiomsFromRepo + statementsFromStorage)
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

    override fun getInconsistencies(): Sequence<Quad> {
        return (additionalContexts + mainContextId).filterNotNull().map(::replaceDefaultGraphId)
            .fold(sequenceOf()) { acc, l ->
                acc + if (l in requestContext.repoContexts || SystemGraphs.isSystemGraph(l)) {
                    requestContext.getStatementForInconsistentRealContext(l)
                } else {
                    (requestContext.inMemoryContexts[l] as? CheckableForConsistency)?.getInconsistencies()
                        ?: emptySequence()
                }
            }
    }
}


fun SituatedInferenceContext.getIdInconsistent(contextId: Long): Sequence<Quad> {
    return (inMemoryContexts[contextId] as? CheckableForConsistency)?.getInconsistencies()
        ?: getStatementForInconsistentRealContext(contextId)
}

fun SituatedInferenceContext.getStatementForInconsistentRealContext(contextId: Long): Sequence<Quad> {
    val isInconsistent = realContextIdToIsConsistent.getOrPut(contextId) {
        val statements =
            repositoryConnection.getStatements(UNBOUND, UNBOUND, UNBOUND, contextId, 0).asSequence()
        val statementsToInsert =
            if (contextId.toInt() == SystemGraphs.EXPLICIT_GRAPH.id) statements.map { it.withField(context = 0) } else statements
        return@getOrPut statementsDisagree(statementsToInsert)
    }

    return if (isInconsistent)
        sequenceOf(Quad(contextId, disagreesWith, contextId, 0))
    else
        emptySequence()
}


fun AbstractRepositoryConnection.getPrettyStringFor(
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

fun AbstractRepositoryConnection.getPrettyStringFor(quad: Quad) =
    getPrettyStringFor(quad.subject, quad.predicate, quad.`object`, quad.context)

fun AbstractRepositoryConnection.getPrettyStringValue(entityId: Long?): String {
    return when (entityId) {
        null -> "null"
        0L -> "unbound"
        else -> try {
            entityPoolConnection.entities[entityId].stringValue()
        } catch (e: Exception) {
            "no entity found"
        }
    }
}