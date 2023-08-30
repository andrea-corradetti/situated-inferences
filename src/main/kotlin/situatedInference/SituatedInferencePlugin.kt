package situatedInference

import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.SystemGraphs
import com.ontotext.trree.sdk.*
import com.ontotext.trree.sdk.Entities.Scope.REQUEST
import com.ontotext.trree.sdk.Entities.Scope.SYSTEM
import com.ontotext.trree.sdk.Entities.UNBOUND
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.util.Values.*
import org.eclipse.rdf4j.model.vocabulary.RDF
import java.util.*


class SituatedInferencePlugin : PluginBase(), Preprocessor, PatternInterpreter,
    ListPatternInterpreter, PluginTransactionListener, Plugin, ParallelTransactionListener {

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
    private val asTripleIri = iri(namespace + "asTriple")
    private val asSingletonIri = iri(namespace + "asSingleton")
    private val reifiesGraphIri = iri(namespace + "reifiesGraph")


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

        rdfSubjectId = pluginConnection.entities.put(RDF.SUBJECT, SYSTEM)
        rdfPredicateId = pluginConnection.entities.put(RDF.PREDICATE, SYSTEM)
        rdfObjectId = pluginConnection.entities.put(RDF.OBJECT, SYSTEM)
        asTripleId = pluginConnection.entities.put(asTripleIri, SYSTEM)
        asSingletonId = pluginConnection.entities.put(asSingletonIri, SYSTEM)
        reifiesGraphId = pluginConnection.entities.put(reifiesGraphIri, SYSTEM)
//        rdfContextId = pluginConnection.entities.put(RDF., SYSTEM)


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

        if (subjectId == UNBOUND && predicateId != situateSchemaId) {
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

        if (predicateId == reifiesGraphId) {
            return 45.0
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

            return StatementIterator.create(subjectId, predicateId, tripleId, contextId)
        }

        if (predicateId == asSingletonId) {
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


            val singletonId = if (objectId != UNBOUND) objectId else entities.put(bnode(), REQUEST)

            requestContext.singletons[singletonId] =
                Singleton(statementId, Quad(reifiedSubject, reifiedPredicate, reifiedObject, singletonId))

            return StatementIterator.create(subjectId, predicateId, singletonId, contextId)
        }

        if (predicateId == reifiesGraphId) {
            val graphId = if (objectId != UNBOUND) objectId else return StatementIterator.EMPTY
            val reifiedGraphId =
                if (subjectId != UNBOUND) subjectId else pluginConnection.entities.put(bnode(), REQUEST)
            val statementsToReify = requestContext.singletons[graphId]?.let { sequenceOf(it.singletonQuad) }
                ?: requestContext.situations[graphId]?.getAll()?.asSequence()
                ?: pluginConnection.statements.get(UNBOUND, UNBOUND, UNBOUND, graphId).asSequence()
            val reifiedStatements = statementsToReify.map { pluginConnection.getReification(it) }.flatten()
            requestContext.inMemoryContexts[reifiedGraphId] =
                InMemoryContext.fromSequence(reifiedGraphId, reifiedStatements)


            return StatementIterator.create(reifiedGraphId, predicateId, graphId, contextId)
        }


        if (predicateId == situateId) {
            return interpret(subjectId, predicateId, longArrayOf(objectId), contextId, pluginConnection, requestContext)
        }

        if (predicateId == situateSchemaId) {
            return handleSituateSchema(subjectId, pluginConnection, requestContext, objectId, contextId)
        }

        if (predicateId == appendToContextsId) {
            return handleAppendToContexts(subjectId, predicateId, objectId, contextId, requestContext, pluginConnection)
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
        requestContext.situations.values.forEach { it.refresh(); /*it.materialize()*/ }

        logger.debug(
            "sequence count {}",
            requestContext.situations[contextId]?.find(subjectId, predicateId, objectId)?.asSequence()
                ?.onEach { logger.debug("QUAD {}", it) }?.count()
        )

        requestContext.singletons[contextId]?.let {
            return StatementIterator.create(
                it.singletonQuad.subject,
                it.singletonQuad.predicate,
                it.singletonQuad.`object`,
                it.singletonQuad.context
            )
        }

        requestContext.inMemoryContexts[contextId]?.let {
            return it.storage.find(subjectId, predicateId, objectId).toStatementIterator()
        }

        requestContext.situations[contextId]?.let {
            return it.find(subjectId, predicateId, objectId).toStatementIterator()
        }


//        requestContext.situations[contextId]?.let {
//            return it.find(subjectId, predicateId, objectId).toStatementIterator()
//        }

//
//        val statements = requestContext.singletons[contextId]?.let { sequenceOf(it.singletonQuad) }
//            ?: requestContext.situations[contextId]?.find(subjectId, predicateId, objectId)?.asSequence()
//            ?:

            return requestContext.situations[contextId]?.find(subjectId, predicateId, objectId)
                ?.toStatementIterator()
                ?: requestContext.situateTasks[contextId]?.findInBoundSituations(subjectId, predicateId, objectId)
                    ?.toStatementIterator()
                ?: if ((requestContext.request as QueryRequest).dataset == null)
                    statementIteratorFromSequence(
                        pluginConnection.statements.get(
                            subjectId,
                            predicateId,
                            objectId,
                            contextId
                        ).asSequence() + requestContext.situations.values.asSequence()
                            .map { it.find(subjectId, predicateId, objectId).asSequence() }
                            .flatten()   //FIXME this is a mess
                    )
                else
                    null


    }

    private fun PluginConnection.getReification(
        it: Quad
    ): Sequence<Quad> {
        val entities = entities
        val statementId = getReifiedStatementId(it.subject, it.predicate, it.`object`)
            ?: entities.put(bnode("${it.subject}-${it.predicate}-${it.`object`}"), REQUEST)

        return sequenceOf(
            Quad(statementId, entities.resolve(RDF.TYPE), entities.resolve(RDF.STATEMENT)),
            Quad(statementId, entities.resolve(RDF.SUBJECT), it.subject),
            Quad(statementId, entities.resolve(RDF.PREDICATE), it.predicate),
            Quad(statementId, entities.resolve(RDF.OBJECT), it.`object`),
        )
    }

    private fun handleAppendToContexts(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        requestContext: SituatedInferenceContext,
        pluginConnection: PluginConnection
    ): StatementIterator? {
        val taskId = if (subjectId.isBound()) subjectId else return StatementIterator.create(
            subjectId,
            predicateId,
            objectId,
            contextId
        )
        requestContext.situateTasks.getOrPut(taskId) { SituateTask(requestContext) }
            .apply { suffixForNewNames = pluginConnection.entities[objectId].stringValue() }

        return StatementIterator.create(taskId, predicateId, objectId, contextId)
    }

    private fun handleSituateSchema(
        subjectId: Long,
        pluginConnection: PluginConnection,
        requestContext: SituatedInferenceContext,
        objectId: Long,
        contextId: Long
    ): StatementIterator? {
        val taskId = if (subjectId.isBound()) subjectId else pluginConnection.entities.put(bnode(), REQUEST)
        val task = requestContext.situateTasks.getOrPut(taskId) { SituateTask(requestContext) }

        val schemaId = if (objectId.isBound()) objectId else pluginConnection.entities.put(bnode(), REQUEST)
        val schema = requestContext.schemas.getOrPut(objectId) { SchemaForSituate(requestContext) }

        task.schema = schema
        schema.boundTasks.add(task)

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
            return null //TODO make empty to bind
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

        val situationId = if (subjectId.isBound()) subjectId else pluginConnection.entities.put(
            bnode("Situation${UUID.randomUUID()}"),
            REQUEST
        )

//        val sharedSituation = requestContext.situations[sharedId]

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

fun StatementIdIterator.toStatementIterator(): StatementIterator {
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

fun replaceDefaultGraphId(it: Long) = when (it) {
    SystemGraphs.RDF4J_NIL.id.toLong() -> SystemGraphs.EXPLICIT_GRAPH.id.toLong() //TODO consider whether readwrite would be more accurate
    else -> it
}

fun Sequence<Quad>.toStatementIterator(): StatementIterator = statementIteratorFromSequence(this)