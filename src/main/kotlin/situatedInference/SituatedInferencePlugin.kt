package situatedInference

import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.SystemGraphs
import com.ontotext.trree.sdk.*
import com.ontotext.trree.sdk.Entities.Scope.REQUEST
import com.ontotext.trree.sdk.Entities.UNBOUND
import org.eclipse.rdf4j.model.util.Values.bnode
import org.eclipse.rdf4j.model.util.Values.iri


class SituatedInferencePlugin : PluginBase(), Preprocessor, PatternInterpreter,
    ListPatternInterpreter, PluginTransactionListener {

    //TODO move to object

    private val namespace = "https://w3id.org/conjectures/"
    private val schemasNamespace = namespace + "schemas"
    private val explainIri = iri(namespace + "explain")
    private val situateIri = iri(namespace + "situate")
    private val sharedIri = iri(namespace + "shared")
    private val situateInsideIri = iri(namespace + "situateInside")
    private val situatedContextPrefixIri = iri(namespace + "SituatedContextPrefix")
    private val sharedKnowledgeContextIri = iri(namespace + "SharedKnowledgeContext")
    private val situatedContextIri = iri(namespace + "SituatedContext")
    private val situateSchemaIri = iri(namespace + "situateSchema")
    private val appendToContextsIri = iri(namespace + "appendToContexts")
    private val hasSituatedContextIri = iri(namespace + "hasSituatedContext")
    private val prefixToSituateIri = iri(namespace + "prefixToSituate")
    private val regexToSituateIri = iri(namespace + "regexToSituate")


    private val defaultGraphId = SystemGraphs.RDF4J_NIL.id.toLong()


    override fun getName() = "Situated-Inference"

    //FIXME this is getting hard to work with
    override fun initialize(reason: InitReason, pluginConnection: PluginConnection) {
        explainId = pluginConnection.entities.put(explainIri, Entities.Scope.SYSTEM)
        situateId = pluginConnection.entities.put(situateIri, Entities.Scope.SYSTEM)
        sharedId = pluginConnection.entities.put(sharedIri, Entities.Scope.SYSTEM)
        situateInsideId = pluginConnection.entities.put(situateInsideIri, Entities.Scope.SYSTEM)
        situatedContextPrefixId = pluginConnection.entities.put(situatedContextPrefixIri, Entities.Scope.SYSTEM)
        sharedKnowledgeContextId = pluginConnection.entities.put(sharedKnowledgeContextIri, Entities.Scope.SYSTEM)
        situatedContextId = pluginConnection.entities.put(situatedContextIri, Entities.Scope.SYSTEM)
        situateSchemaId = pluginConnection.entities.put(situateSchemaIri, Entities.Scope.SYSTEM)
        appendToContextsId = pluginConnection.entities.put(appendToContextsIri, Entities.Scope.SYSTEM)
        hasSituatedContextId = pluginConnection.entities.put(hasSituatedContextIri, Entities.Scope.SYSTEM)
        prefixToSituateId = pluginConnection.entities.put(prefixToSituateIri, Entities.Scope.SYSTEM)
        regexToSituateId = pluginConnection.entities.put(regexToSituateIri, Entities.Scope.SYSTEM)

        logger.debug("Initialized: explainId $explainId, situateId $situateId")
    }


    override fun preprocess(request: Request): RequestContext =
        SituatedInferenceContext.fromRequest(request, logger).apply { sharedScope = sharedId }

    override fun estimate(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: RequestContext
    ): Double {

        if (subjectId == UNBOUND && predicateId != situateSchemaId){
            return 50.0
        }

        if (predicateId == situateSchemaId) {
            return 10.0
        }



        if (predicateId == appendToContextsId) {
            return 20.0
        }

        if (pluginConnection.entities.get(contextId)?.stringValue()?.startsWith(schemasNamespace) == true) {
            return 20.0
        }

        if (predicateId == hasSituatedContextId) {
            return 40.0
        }

        return Double.MAX_VALUE
    }

    override fun interpret(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: RequestContext?
    ): StatementIterator? {
        if (requestContext !is SituatedInferenceContext) {
            return null
        }

        if (predicateId == situateId) {
            return interpret(subjectId, predicateId, longArrayOf(objectId), contextId, pluginConnection, requestContext)
        }

        if (predicateId == situateSchemaId) {
            val taskId = if (subjectId.isBound()) subjectId else pluginConnection.entities.put(bnode(), REQUEST)
            val task = requestContext.situateTasks.getOrPut(taskId) { SituateTask(requestContext) }

            val schemaId = if (objectId.isBound()) objectId else pluginConnection.entities.put(bnode(), REQUEST)
            val schema = requestContext.schemas.getOrPut(objectId) { SchemaForSituate(requestContext) }

            task.schema = schema
            schema.boundTasks.add(task)

            return StatementIterator.create(taskId, situateSchemaId, schemaId, contextId)
        }

        if (predicateId == appendToContextsId) {
            val taskId = if (subjectId.isBound()) subjectId else return StatementIterator.create(subjectId, predicateId, objectId, contextId)
            requestContext.situateTasks.getOrPut(taskId) { SituateTask(requestContext) }
                .apply { suffixForNewNames = pluginConnection.entities[objectId].stringValue() }

            return StatementIterator.create(taskId, predicateId, objectId, contextId)
        }

        if (pluginConnection.entities[contextId]?.stringValue()?.startsWith(namespace + "schemas") == true) {
            return handleSchemaStatement(subjectId, predicateId, objectId, contextId, requestContext)
        }

        if (predicateId == hasSituatedContextId) {
            val taskId = if (subjectId.isBound()) subjectId else return StatementIterator.EMPTY
            val task = requestContext.situateTasks.getOrPut(taskId) { SituateTask(requestContext) }


            task.createSituations()

            return statementIteratorFromSequence(
                task.createdSituationsIds.asSequence().map { Quad(taskId, hasSituatedContextId, it, contextId) }
            )
        }

        requestContext.situateTasks.values.forEach { it.createSituations() } //TODO rewrite so this is unnecessary

//        logger.debug("context for find {}", pluginConnection.entities[contextId]?.stringValue() ?: "null")
//        requestContext.situations[contextId]?.find(subjectId, predicateId, objectId)?.asSequence()?.forEach {
//            logger.debug("QUAD {}", it)
//        } ?: logger.debug("returned null")

        if (requestContext.situations.keys.contains(contextId)) {
            logger.debug(
                "sequence count {}",
                requestContext.situations[contextId]!!.find(subjectId, predicateId, objectId).asSequence()
                    .onEach { logger.debug("QUAD {}", it) }.count()
            )
        }

        return requestContext.situations[contextId]?.find(subjectId, predicateId, objectId)?.toStatementIterator()
            ?: requestContext.situateTasks[contextId]?.findInBoundSituations(subjectId, predicateId, objectId)
                ?.toStatementIterator()
            ?: if ((requestContext.request as QueryRequest).dataset != null) null else StatementIterator.EMPTY


    }

    private fun handleSchemaStatement(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        requestContext: SituatedInferenceContext
    ): StatementIterator? {
        val schema = requestContext.schemas.getOrPut(contextId) { SchemaForSituate(requestContext) }
        val parsed = schema.parse(subjectId, predicateId, objectId)
        return if (parsed) StatementIterator.create(subjectId, predicateId, objectId, contextId) else null
    }


    override fun estimate(
        p0: Long, p1: Long, p2: LongArray?, p3: Long, p4: PluginConnection?, requestContext: RequestContext?
    ): Double {
        if (requestContext !is SituatedInferenceContext) {
            return Double.MAX_VALUE
        }
        return 1.0
    }

    //For list objects
    override fun interpret(
        subjectId: Long,
        predicateId: Long,
        objectsIds: LongArray?,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: RequestContext?
    ): StatementIterator? {
        if (requestContext !is SituatedInferenceContext || objectsIds == null || objectsIds.isEmpty()) {
            return null
        }

        return when (predicateId) {
            situateId -> handleSituate(subjectId, objectsIds, pluginConnection, requestContext)
            explainId -> handleExplain(subjectId, objectsIds, contextId, pluginConnection, requestContext)

            else -> null
        }
    }


    private fun handleExplain(
        subjectId: Long,
        objectsIds: LongArray,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: SituatedInferenceContext
    ): StatementIterator? {
        if (!requestContext.isInferenceEnabled) {
            logger.info("Inferencer is not enabled - skipping antecedents search")
            return null
        }
        if (objectsIds.size < 3 || objectsIds.slice(0..2).any { it == UNBOUND }) {
            return null
        }

        val (subjectToExplain, predicateToExplain, objectToExplain) = objectsIds

        val explainTaskId = if (subjectId != UNBOUND) subjectId else pluginConnection.entities.put(bnode(), REQUEST)

        requestContext.explainTasks[explainTaskId]

        return StatementIterator.EMPTY //TODO finish this
    }

    private fun handleSituate(
        subjectId: Long,
        objectsIds: LongArray,
        pluginConnection: PluginConnection,
        requestContext: SituatedInferenceContext
    ): StatementIterator? {
        objectsIds.map { pluginConnection.entities[it] }.filter { !it.isBNode && !it.isIRI }.let { values ->
            if (values.isNotEmpty()) {
                val message = values.joinToString("\n") { "${it.stringValue()} in object list isn't a valid graph." }
                throw PluginException(message)
            }
        }

        val situationId = if (subjectId.isBound()) subjectId else pluginConnection.entities.put(bnode(), REQUEST)

        val sharedSituation = requestContext.situations[sharedId]

        requestContext.situations[situationId] = Situation(
            requestContext, situationId, objectsIds.toSet()
        )

        return StatementIterator.create(
            objectsIds.map { longArrayOf(situationId, situateId, it, 0L) }.toTypedArray()
        )
    }


//    private fun getAntecedentsWithRule(
//        quad: Quad, pluginConnection: PluginConnection, requestContext: SituatedInferenceContext
//    ): MutableSet<Solution?> {
//        val reificationId = pluginConnection.entities.put(bnode(), REQUEST)
//        val statementProps = requestContext.repositoryConnection.getExplicitStatementProps(quad.asTriple())
//        return ExplainIter(requestContext, reificationId, 0, quad, statementProps).solutions
//    }


    private fun AbstractRepositoryConnection.getExplicitStatementProps(
        triple: Triple
    ): ExplicitStatementProps = this.getExplicitStatementProps(triple.subject, triple.predicate, triple.`object`)

    private fun AbstractRepositoryConnection.getExplicitStatementProps(
        subjToExplain: Long, predToExplain: Long, objToExplain: Long,
    ): ExplicitStatementProps {

        val iterForExplicit = getStatements(
            subjToExplain, predToExplain, objToExplain, excludeDeletedHiddenInferred
        )
        iterForExplicit.use {
            logger.debug("context in explicit props " + iterForExplicit.context)
            // handle if explicit comes from sameAs
            return ExplicitStatementProps(
                isExplicit = iterForExplicit.hasNext(),
                explicitContext = iterForExplicit.context,
                isDerivedFromSameAs = iterForExplicit.status and StatementIdIterator.SKIP_ON_REINFER_STATEMENT_STATUS != 0
            )
        }
    }


    private fun Long.isBound(): Boolean = this != 0L

    private fun StatementIdIterator.toStatementIterator(): StatementIterator {
        return object : StatementIterator() {
            override fun next(): Boolean {
                if (this@toStatementIterator.hasNext()) {
                    subject = this@toStatementIterator.subj
                    predicate = this@toStatementIterator.pred
                    `object` = this@toStatementIterator.obj
                    context = this@toStatementIterator.context
                    this@toStatementIterator.next()
                    return true
                }
                return false

            }

            override fun close() {
                this@toStatementIterator.close()
            }
        }

    }

    override fun transactionStarted(p0: PluginConnection?) {
        logger.debug("transaction started")
    }

    override fun transactionCommit(p0: PluginConnection) {
        logger.debug("transaction commit")
    }

    override fun transactionCompleted(p0: PluginConnection?) {
        logger.debug("transaction completed")

    }

    override fun transactionAborted(p0: PluginConnection?) {
        logger.debug("transaction aborted")
    }
}

fun StatementIterator.asSequence() = sequence {
    while (next()) {
        yield(Quad(subject, predicate, `object`, context))
    }
    this@asSequence.close()
}.constrainOnce()


fun StatementIdIterator.asSequence() = sequence {
    while (hasNext()) {
        yield(Quad(subj, pred, obj, context, status))
        next()
    }
    this@asSequence.close()
}.constrainOnce()

fun statementIteratorFromSequence(sequence: Sequence<Quad>) = object : StatementIterator() {
    val iterator = sequence.iterator()

    override fun next(): Boolean {
        if (iterator.hasNext()) {
            val current = iterator.next()
            this.subject = current.subject
            this.predicate = current.predicate
            this.`object` = current.`object`
            this.context = current.context
            return true
        }
        return false
    }

    override fun close() {}

}

fun replaceDefaultGraphId(it: Long) = when (it) {
    SystemGraphs.RDF4J_NIL.id.toLong() -> SystemGraphs.EXPLICIT_GRAPH.id.toLong() //TODO consider whether readwrite would be more accurate
    else -> it
}