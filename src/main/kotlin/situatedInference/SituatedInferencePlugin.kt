package situatedInference

import com.ontotext.trree.SystemGraphs
import com.ontotext.trree.sdk.*
import com.ontotext.trree.sdk.Entities.Scope.REQUEST
import com.ontotext.trree.sdk.Entities.Scope.SYSTEM
import com.ontotext.trree.sdk.Entities.UNBOUND
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Triple
import org.eclipse.rdf4j.model.util.Values.*
import org.eclipse.rdf4j.model.vocabulary.RDF
import java.util.*


class SituatedInferencePlugin : PluginBase(), Preprocessor, PatternInterpreter,
    ListPatternInterpreter, PluginTransactionListener, Plugin, ParallelTransactionListener {

    //TODO move to object

    private val namespace = "https://w3id.org/conjectures/"
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
    private val asTripleIri = iri(namespace + "asTriple")
    private val asSingletonIri = iri(namespace + "asSingleton")
    private val reifiesGraphIri = iri(namespace + "reifiesGraph")
    private val graphFromEmbeddedIri = iri(namespace + "graphFromEmbedded")
    private val testBlankIri = iri(namespace + "testBlank")
    private val groupsTripleIri = iri(namespace + "groupsTriple")

    override fun getName() = "Situated-Inference"

    //FIXME this is getting hard to work with
    override fun initialize(reason: InitReason, pluginConnection: PluginConnection) {
        explainId = pluginConnection.entities.put(iri(namespace + "explain"), Entities.Scope.SYSTEM)
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

        rdfSubjectId = pluginConnection.entities.put(RDF.SUBJECT, SYSTEM)
        rdfPredicateId = pluginConnection.entities.put(RDF.PREDICATE, SYSTEM)
        rdfObjectId = pluginConnection.entities.put(RDF.OBJECT, SYSTEM)
        asTripleId = pluginConnection.entities.put(asTripleIri, SYSTEM)
        asSingletonId = pluginConnection.entities.put(asSingletonIri, SYSTEM)
        reifiesGraphId = pluginConnection.entities.put(reifiesGraphIri, SYSTEM)
        graphFromEmbeddedId = pluginConnection.entities.put(graphFromEmbeddedIri, SYSTEM)
        testBlankId = pluginConnection.entities.put(testBlankIri, SYSTEM)
        groupsTripleId = pluginConnection.entities.put(groupsTripleIri, SYSTEM)

//        rdfContextId = pluginConnection.entities.put(RDF., SYSTEM)


        logger.debug("Initialized: explainId $explainId, situateId $situateId")
    }


    override fun preprocess(request: Request): RequestContext =
        SituatedInferenceContext.fromRequest(request, logger)

    override fun estimate(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: RequestContext
    ): Double {
        if (subjectId == UNBOUND && predicateId == UNBOUND && objectId == UNBOUND && contextId == UNBOUND) {
            return 10000.0
        }

        if (subjectId == UNBOUND && predicateId != situateSchemaId && predicateId < 0) {
            return 100.0
        }

        if (pluginConnection.entities.get(contextId)?.stringValue()?.startsWith(namespace + "schemas") == true) {
            if (subjectId == UNBOUND) return 10000.0
            return 10.0
        }

        if (predicateId > 0) {
            return 5.0
        }

        if (predicateId == graphFromEmbeddedId) {
            return 9.0
        }




        if (predicateId == situateSchemaId) {
            return 20.0
        }

        if (predicateId == situateId) {
            return 20.0
        }


        if (predicateId == hasSituatedContextId) {
            return 40.0
        }


        if (predicateId == reifiesGraphId) {
            return 45.0
        }

        return 9000.0
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

        if ((requestContext.request as QueryRequest).hasSystemGraphs()) {
            return null
        }

        if (predicateId == testBlankId) {
            val statements = pluginConnection.statements.get(subjectId, predicateId, objectId)

        }
        if (predicateId == situatedContextId) {
            val statements = pluginConnection.statements.get(subjectId, predicateId, objectId)

        }

        if (predicateId == graphFromEmbeddedId) {
            val entities = pluginConnection.entities
            val tripleId = if (objectId != UNBOUND) objectId else return StatementIterator.EMPTY
            val triple = (entities[tripleId] as? Triple) ?: return StatementIterator.EMPTY
            val graphId =
                if (subjectId != UNBOUND) subjectId else entities.put(iri(namespace, "triple-${objectId}"), REQUEST)
            val graph = requestContext.inMemoryContexts.getOrPut(graphId) {
                SituatedContext(
                    situatedContextId = graphId,
                    sourceId = tripleId,
                    requestContext = requestContext
                )
            } as ContextWithStorage
            graph.add(
                entities.resolve(triple.subject),
                entities.resolve(triple.predicate),
                entities.resolve(triple.`object`)
            )
            return StatementIterator.create(graphId, graphFromEmbeddedId, tripleId, contextId)
        }

        if (predicateId == groupsTripleId) {
            if (subjectId == UNBOUND || objectId == UNBOUND) return StatementIterator.EMPTY
            val triple = pluginConnection.entities[objectId] as? Triple ?: return StatementIterator.EMPTY
            val context =
                requestContext.inMemoryContexts.getOrPut(subjectId) { SituatedContext(
                    situatedContextId = subjectId,
                    sourceId = objectId,
                    requestContext = requestContext
                ) } as ContextWithStorage
            context.add(
                pluginConnection.entities.resolve(triple.subject),
                pluginConnection.entities.resolve(triple.predicate),
                pluginConnection.entities.resolve(triple.`object`)
            )
            return StatementIterator.create(subjectId, groupsTripleId, objectId, contextId)
        }


        if (predicateId == asTripleId) {
            val entities = pluginConnection.entities
            val statements = pluginConnection.statements
            val statementId = if (subjectId != UNBOUND) subjectId else return StatementIterator.EMPTY
            val isReifiedStatement =
                statements.get(statementId, entities.resolve(RDF.TYPE), entities.resolve(RDF.STATEMENT)).asSequence()
                    .any()

            if (!isReifiedStatement) {
                return StatementIterator.EMPTY
            }
            val reifiedSubject =
                statements.get(statementId, entities.resolve(RDF.SUBJECT), UNBOUND).asSequence().firstOrNull()?.`object`
                    ?: return StatementIterator.EMPTY
            val reifiedPredicate =
                statements.get(statementId, entities.resolve(RDF.PREDICATE), UNBOUND).asSequence()
                    .firstOrNull()?.`object`
                    ?: return StatementIterator.EMPTY
            val reifiedObject =
                statements.get(statementId, entities.resolve(RDF.OBJECT), UNBOUND).asSequence().firstOrNull()?.`object`
                    ?: return StatementIterator.EMPTY
            val triple = triple(
                entities.get(reifiedSubject) as Resource,
                entities.get(reifiedPredicate) as IRI?,
                entities.get(reifiedObject)
            )
            val tripleId = entities.put(triple, REQUEST)

            return StatementIterator.create(statementId, asTripleId, tripleId, contextId)
        }

        if (predicateId == asSingletonId) {
            return handleAsSingleton(pluginConnection, subjectId, objectId, requestContext, contextId)
        }

        if (predicateId == reifiesGraphId) {
            val graphToReify = if (objectId != UNBOUND) objectId else return StatementIterator.EMPTY
            val name by lazy { pluginConnection.entities[graphToReify]!!.stringValue() + "-reified" }
            val reificationId =
                if (subjectId != UNBOUND) subjectId else pluginConnection.entities.put(iri(name), REQUEST)
            val statementsToReify = requestContext.inMemoryContexts[graphToReify]?.getAll()
                ?: pluginConnection.statements.get(UNBOUND, UNBOUND, UNBOUND, graphToReify).asSequence()
            val reifiedStatements = statementsToReify.map { pluginConnection.getReification(it) }.flatten()
            requestContext.inMemoryContexts[reificationId] =
                ReifiedContext.fromSequence(reifiedStatements, graphToReify, reificationId, requestContext)

            return StatementIterator.create(reificationId, predicateId, graphToReify, contextId)
        }


        if (predicateId == situateId) {
            return interpret(subjectId, predicateId, longArrayOf(objectId), contextId, pluginConnection, requestContext)
        }

        if (predicateId == situateSchemaId) {
            return handleSituateSchema(subjectId, pluginConnection, requestContext, objectId, contextId)
        }

        if (pluginConnection.entities[contextId]?.stringValue()?.startsWith(namespace + "schemas") == true) {
            return handleSchemaStatement(subjectId, predicateId, objectId, contextId, requestContext)
        }

        if (predicateId == hasSituatedContextId) {
            val taskId = if (subjectId.isBound()) subjectId else return StatementIterator.EMPTY
            val task = requestContext.situateTasks.getOrPut(taskId) { SituateTask(taskId, requestContext) }

            task.createSituationsIfReady()

            return if (task.createdSituationsIds.isNotEmpty())
                statementIteratorFromSequence(
                    task.createdSituationsIds.asSequence().map { Quad(taskId, hasSituatedContextId, it, contextId) })
            else StatementIterator.EMPTY
        }


        val statements = if (contextId == UNBOUND)
            (pluginConnection.statements.get(subjectId, predicateId, objectId, contextId).asSequence() +
                    requestContext.inMemoryContexts.findInAll(subjectId, predicateId, objectId, contextId))
        else
            requestContext.inMemoryContexts[contextId]?.find(subjectId, predicateId, objectId)
                ?: pluginConnection.statements.get(subjectId, predicateId, objectId, contextId).asSequence()

        //TODO may be redundant
        val quotingSubject =
            (requestContext.inMemoryContexts[subjectId] as? Quotable)?.getQuotingAsSubject()?.getAll()
                ?: emptySequence()

        val quotingObject =
            (requestContext.inMemoryContexts[objectId] as? Quotable)?.getQuotingAsObject()?.getAll() ?: emptySequence()

        return (statements + quotingSubject + quotingObject).toStatementIterator()
    }

    private fun handleAsSingleton(
        pluginConnection: PluginConnection,
        subjectId: Long,
        objectId: Long,
        requestContext: SituatedInferenceContext,
        contextId: Long
    ): StatementIterator? {
        val entities = pluginConnection.entities
        val statements = pluginConnection.statements
        val statementId = if (subjectId != UNBOUND) subjectId else return StatementIterator.EMPTY
        val isReifiedStatement =
            statements.get(statementId, entities.resolve(RDF.TYPE), entities.resolve(RDF.STATEMENT)).asSequence()
                .any()

        if (!isReifiedStatement) {
            return StatementIterator.EMPTY
        }
        val reifiedSubject =
            statements.get(statementId, entities.resolve(RDF.SUBJECT), UNBOUND).asSequence().firstOrNull()?.`object`
                ?: return StatementIterator.EMPTY
        val reifiedPredicate =
            statements.get(statementId, entities.resolve(RDF.PREDICATE), UNBOUND).asSequence()
                .firstOrNull()?.`object`
                ?: return StatementIterator.EMPTY
        val reifiedObject =
            statements.get(statementId, entities.resolve(RDF.OBJECT), UNBOUND).asSequence().firstOrNull()?.`object`
                ?: return StatementIterator.EMPTY

        val name by lazy { entities[statementId]!!.stringValue() + "-singleton" }
        val singletonId = if (objectId != UNBOUND) objectId else entities.put(iri(name), REQUEST)

        requestContext.inMemoryContexts[singletonId] =
            Singleton(statementId, Quad(reifiedSubject, reifiedPredicate, reifiedObject, singletonId), requestContext)

        requestContext.statementIdToSingletonId[statementId] = singletonId

        return StatementIterator.create(statementId, asSingletonId, singletonId, contextId)
    }


//    private fun handleAppendToContexts(
//        subjectId: Long,
//        predicateId: Long,
//        objectId: Long,
//        contextId: Long,
//        requestContext: SituatedInferenceContext,
//        pluginConnection: PluginConnection
//    ): StatementIterator? {
//        val taskId = if (subjectId.isBound()) subjectId else return  StatementIterator.create(subjectId, predicateId, objectId, contextId)
//        requestContext.situateTasks.getOrPut(taskId) { SituateTask(requestContext) }
//            .apply { suffixForNewNames = pluginConnection.entities[objectId].stringValue() }
//
//        return StatementIterator.create(taskId, predicateId, objectId, contextId)
//    }

    private fun handleSituateSchema(
        subjectId: Long,
        pluginConnection: PluginConnection,
        requestContext: SituatedInferenceContext,
        objectId: Long,
        contextId: Long
    ): StatementIterator? {
        val taskId = if (subjectId.isBound()) subjectId else pluginConnection.entities.put(bnode(), REQUEST)
        val schemaId = if (objectId.isBound()) objectId else pluginConnection.entities.put(bnode(), REQUEST)
        val task = requestContext.situateTasks.getOrPut(taskId) { SituateTask(taskId, requestContext) }
            .apply { this.schemaId = schemaId }
        val schema = requestContext.schemas.getOrPut(schemaId) { SchemaForSituate(requestContext) }
            .apply { boundTasks.add(task) }

        task.createSituationsIfReady()

//        val sequence = sequenceOf(Quad(taskId, situateSchemaId, schemaId, contextId)) +
//                task.createdSituationsIds.map { Quad(taskId, hasSituatedContextId, it, 0) }.asSequence()
//
//        return sequence.toStatementIterator()
        return StatementIterator.create(taskId, situateSchemaId, schemaId, contextId)
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
        schema.boundTasks.onEach { it.createSituationsIfReady() }
        return if (parsed)
            StatementIterator.create(subjectId, predicateId, objectId, contextId)
        else
            StatementIterator.EMPTY
    }


    override fun estimate(
        p0: Long, p1: Long, p2: LongArray?, p3: Long, p4: PluginConnection?, requestContext: RequestContext?
    ): Double {
        if (p1 == situateId) {
            if (p2?.any { it == UNBOUND } == true) {
                return 10000.0
            }
            return 20.0
        }

        return 10000.0


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
            return null //TODO make empty to bind
        }

        val (subjectToExplain, predicateToExplain, objectToExplain) = objectsIds

        val explainTaskId = if (subjectId != UNBOUND) subjectId else pluginConnection.entities.put(bnode(), REQUEST)

        requestContext.explainTasks[explainTaskId]

        TODO()
    }

    private fun handleSituate(
        subjectId: Long,
        objectsIds: LongArray,
        pluginConnection: PluginConnection,
        requestContext: SituatedInferenceContext
    ): StatementIterator? {

        if (objectsIds.any { it == UNBOUND }) {
            return StatementIterator.EMPTY
        }

        val contextsInRepository by lazy {
            requestContext.repositoryConnection.contextIDs.asSequence().map { it.context }
        }
        if (objectsIds.any { it !in requestContext.inMemoryContexts.keys && it != -36L && it !in contextsInRepository }) {
            return StatementIterator.EMPTY
        }

        objectsIds.map { pluginConnection.entities[it] }.filter { !it.isBNode && !it.isIRI }.let { values ->
            if (values.isNotEmpty()) {
                val message = values.joinToString("\n") { "${it.stringValue()} in object list isn't a valid graph." }
                throw PluginException(message)
            }
        }

        val sourceId =
            (requestContext.inMemoryContexts[objectsIds.first()] as? Quotable)?.sourceId ?: objectsIds.first()

        val situationId = if (subjectId != UNBOUND) subjectId else pluginConnection.entities.put(
            iri(namespace + "situations/${UUID.randomUUID()}"),
            REQUEST
        )
//        requestContext.inMemoryContexts[situationId] = Situation(
//            situationId, objectsIds.toSet(), requestContext
//        ).apply { refresh() }

        requestContext.inMemoryContexts[situationId] = SituatedContext(
            situationId, sourceId, objectsIds.first(), objectsIds.toSet(), requestContext
        ).apply { refresh() }

        return StatementIterator.create(
            objectsIds.map { longArrayOf(situationId, situateId, it, 0L) }.toTypedArray()
        )
//        return StatementIterator.create(situationId, situateId, 0, 0)
    }

    private fun Long.isBound(): Boolean = this != 0L

    override fun transactionStarted(p0: PluginConnection?) {
        logger.debug("transaction started")
    }

    override fun transactionCommit(p0: PluginConnection) {
    }

    override fun transactionCompleted(p0: PluginConnection?) {
        logger.debug("transaction completed")

    }

    override fun transactionAborted(p0: PluginConnection?) {
        logger.debug("transaction aborted")
    }

    private fun PluginConnection.getReification(
        it: Quad
    ): Sequence<Quad> {
        val entities = entities
        val statementId = getReifiedStatementId(it.subject, it.predicate, it.`object`)
            ?: entities.put(iri(namespace, "reifications/${it.subject}-${it.predicate}-${it.`object`}"), REQUEST)

        return sequenceOf(
            Quad(statementId, entities.resolve(RDF.TYPE), entities.resolve(RDF.STATEMENT)),
            Quad(statementId, entities.resolve(RDF.SUBJECT), it.subject),
            Quad(statementId, entities.resolve(RDF.PREDICATE), it.predicate),
            Quad(statementId, entities.resolve(RDF.OBJECT), it.`object`),
        )
    }
}

private fun PluginConnection.getReifiedStatementId(subject: Long, predicate: Long, `object`: Long): Long? {
    val matchSubject =
        statements.get(0, entities.resolve(RDF.SUBJECT), subject).asSequence().map { it.subject }
            .toSet()
    val matchPredicate =
        statements.get(0, entities.resolve(RDF.PREDICATE), predicate).asSequence().map { it.subject }
            .toSet()
    val matchObject =
        statements.get(0, entities.resolve(RDF.OBJECT), `object`).asSequence().map { it.subject }
            .toSet()

    return (matchSubject intersect matchPredicate intersect matchObject).firstOrNull()
}

fun QueryRequest.hasSystemGraphs() =
    this.dataset?.defaultGraphs?.any { it.namespace.startsWith(SystemGraphs.NAMESPACE) } == true

fun replaceDefaultGraphId(it: Long) = when (it) {
    SystemGraphs.RDF4J_NIL.id.toLong() -> SystemGraphs.EXPLICIT_GRAPH.id.toLong() //TODO consider whether readwrite would be more accurate
    else -> it
}

fun Quad.replaceValues(
    oldId: Long,
    newId: Long
): Quad = withField(
    subject = if (subject == oldId) newId else subject,
    predicate = if (predicate == oldId) newId else predicate,
    `object` = if (`object` == oldId) newId else `object`,
    context = if (context == oldId) newId else context,
)

