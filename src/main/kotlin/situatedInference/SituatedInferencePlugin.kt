package situatedInference

import com.ontotext.trree.AbstractRepository.IMPLICIT_GRAPH
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.sdk.*
import com.ontotext.trree.sdk.Entities.Scope.REQUEST
import org.eclipse.rdf4j.model.util.Values.bnode
import org.eclipse.rdf4j.model.util.Values.iri
import proof.ProofPlugin.excludeDeletedHiddenInferred
import kotlin.properties.Delegates


const val UNBOUND = 0L

class SituatedInferencePlugin : PluginBase(), Preprocessor, PluginTransactionListener, PatternInterpreter,
    ListPatternInterpreter {

    private val namespace = "https://w3id.org/conjectures/"
    private val explainIri = iri(namespace + "explain")
    private val situateIri = iri(namespace + "situate")
    private var explainId by Delegates.notNull<Long>()
    private var situateId by Delegates.notNull<Long>()

    override fun getName() = "Situated-Inference"

    override fun initialize(reason: InitReason, pluginConnection: PluginConnection) {
        explainId = pluginConnection.entities.put(explainIri, Entities.Scope.SYSTEM)
        situateId = pluginConnection.entities.put(situateIri, Entities.Scope.SYSTEM)
        logger.debug("Initialized: explainId $explainId, situateId $situateId")
    }

    override fun preprocess(request: Request): RequestContext {
        return SituatedInferenceContext.fromRequest(request, logger)
    }

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

    private fun Solution.areAntecedentsInScope(contexts: Collection<Long>): Boolean {
        return contexts.any { context -> antecedents.all { it.context == context } }
    }

    private fun AbstractRepositoryConnection.insertStatementsInScope(
        statementsInScope: List<Map.Entry<Quad, MutableSet<Solution?>>>
    ) {
        statementsInScope.forEach { (quad, solutions) ->
            val contextsForQuad = solutions.filterNotNull()
                .fold(listOf<Long>()) { acc, solution -> acc + solution.antecedents.map { it.context } }
                .filterNot { it.toInt() == IMPLICIT_GRAPH }
            contextsForQuad.forEach { context ->
                putStatement(
                    quad.subject,
                    quad.predicate,
                    quad.`object`,
                    context,
                    0,
                )
            }
        }
    }

    private fun AbstractRepositoryConnection.removeStatementsOutOfScope(
        statementsOutOfScope: List<Map.Entry<Quad, MutableSet<Solution?>>>
    ) {
        statementsOutOfScope.forEach { (quad, _) ->
            removeStatements(
                quad.subject, quad.predicate, quad.`object`
            )
        }
    }


    override fun estimate(
        p0: Long, p1: Long, p2: LongArray?, p3: Long, p4: PluginConnection?, p5: RequestContext?
    ): Double {
        TODO("Not yet implemented")
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

        val statementsInScope = objectsIds.asSequence().map { contextInScope ->
            pluginConnection.statements.get(UNBOUND, UNBOUND, UNBOUND, contextInScope).asSequence()
        }.flatten()

        requestContext.situations[situationId] = SituationIter(
            requestContext, situationId, situateId, 0,      //TODO replace this with list of contexts
            statementsInScope
        ).apply { inferImplicitStatements() }

        return StatementIterator.create(
            objectsIds.map { longArrayOf(situationId, situateId, it, 0L) }.toTypedArray()
        )
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
}

fun StatementIdIterator.asSequence() = sequence {
    while (hasNext()) {
        val quad = Quad(subj, pred, obj, context, status)
        yield(quad)
        next()
    }
}
