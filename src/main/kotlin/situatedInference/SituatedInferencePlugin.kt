package situatedInference

import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.SystemGraphs
import com.ontotext.trree.sdk.*
import com.ontotext.trree.sdk.Entities.Scope.REQUEST
import org.eclipse.rdf4j.model.util.Values.bnode
import org.eclipse.rdf4j.model.util.Values.iri
import kotlin.properties.Delegates


const val UNBOUND = 0L

class SituatedInferencePlugin : PluginBase(), Preprocessor, PluginTransactionListener, PatternInterpreter,
    ListPatternInterpreter {

    private val namespace = "https://w3id.org/conjectures/"
    private val explainIri = iri(namespace + "explain")
    private var explainId by Delegates.notNull<Long>()
    private val situateIri = iri(namespace + "situate")
    private var situateId by Delegates.notNull<Long>()
    private val sharedIri = iri(namespace + "shared")
    private var sharedId by Delegates.notNull<Long>()

    private val defaultGraphId = SystemGraphs.RDF4J_NIL.id.toLong()

    override fun getName() = "Situated-Inference"

    override fun initialize(reason: InitReason, pluginConnection: PluginConnection) {
        explainId = pluginConnection.entities.put(explainIri, Entities.Scope.SYSTEM)
        situateId = pluginConnection.entities.put(situateIri, Entities.Scope.SYSTEM)
        sharedId = pluginConnection.entities.put(sharedIri, Entities.Scope.SYSTEM)
        logger.debug("Initialized: explainId $explainId, situateId $situateId")
    }

    override fun preprocess(request: Request): RequestContext =
        SituatedInferenceContext.fromRequest(request, logger).apply { sharedScope = sharedId }

    override fun estimate(p0: Long, p1: Long, p2: Long, p3: Long, p4: PluginConnection?, p5: RequestContext?): Double {
        return 1.0 //TODO
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

        val situation = requestContext.situations[contextId] ?: return null
        return situation.find(subjectId, predicateId, objectId).toStatementIterator()
    }

    override fun estimate(
        p0: Long, p1: Long, p2: LongArray?, p3: Long, p4: PluginConnection?, p5: RequestContext?
    ): Double {
        return 1.0 //TODO("Not yet implemented")
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
        if (requestContext !is SituatedInferenceContext || predicateId != situateId) { //TODO remove this guard
            return null
        }
        if (objectsIds == null || objectsIds.isEmpty()) {
            return null
        }

        objectsIds.map { pluginConnection.entities[it] }.filter { !it.isBNode && !it.isIRI }.let { values ->
            if (values.isNotEmpty()) {
                val message = values.joinToString("\n") { "${it.stringValue()} in object list isn't a valid graph." }
                throw PluginException(message)
            }
        }

        val situationId = if (subjectId.isBound()) subjectId else pluginConnection.entities.put(bnode(), REQUEST)

        val statementsInScope =
            (objectsIds + sharedId).asSequence().map(::replaceDefaultGraphId).map { contextInScope ->
                requestContext.situations[contextInScope]?.getAll()?.asSequence()
                    ?: pluginConnection.statements.get(UNBOUND, UNBOUND, UNBOUND, contextInScope).asSequence()
            }.flatten()

        requestContext.situations[situationId] = Situation(
            requestContext, situationId, situateId, objectsIds.toSet(), statementsInScope
        ).apply { inferImplicitStatements() }

        return StatementIterator.create(
            objectsIds.map { longArrayOf(situationId, situateId, it, 0L) }.toTypedArray()
        )
    }

    private fun replaceDefaultGraphId(it: Long) = when (it) {
        defaultGraphId -> SystemGraphs.EXPLICIT_GRAPH.id.toLong()
        else -> it
    }

    private fun getAntecedentsWithRule(
        quad: Quad, pluginConnection: PluginConnection, requestContext: SituatedInferenceContext
    ): MutableSet<Solution?> {
        val reificationId = pluginConnection.entities.put(bnode(), REQUEST)
        val statementProps = requestContext.repositoryConnection.getExplicitStatementProps(quad.asTriple())
        return ExplainIter(requestContext, reificationId, 0, quad, statementProps).solutions
    }


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


    override fun transactionStarted(p0: PluginConnection?) {}

    override fun transactionCommit(p0: PluginConnection?) {}

    override fun transactionCompleted(p0: PluginConnection?) {
    }

    override fun transactionAborted(p0: PluginConnection?) {
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
