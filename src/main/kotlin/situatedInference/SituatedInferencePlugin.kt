package situatedInference

import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.sdk.*
import org.eclipse.rdf4j.model.util.Values.bnode
import org.eclipse.rdf4j.model.util.Values.iri
import proof.ProofPlugin.excludeDeletedHiddenInferred
import kotlin.properties.Delegates


class SituatedInferencePlugin : PluginBase(), Preprocessor, StatementListener, PluginTransactionListener,
    PatternInterpreter {

    private var implicitStatements = mutableListOf<Quad>()

    private val namespace = "https://w3id.org/conjectures/"
    private val explainUri = iri(namespace + "explain")
    private val situateUri = iri(namespace + "situate")
    private var explainId by Delegates.notNull<Long>()
    private var situateId by Delegates.notNull<Long>()

    override fun getName() = "Situated-Inference"

    override fun initialize(reason: InitReason, pluginConnection: PluginConnection) {
        explainId = pluginConnection.entities.put(explainUri, Entities.Scope.SYSTEM)
        situateId = pluginConnection.entities.put(situateUri, Entities.Scope.SYSTEM)
        logger.debug("explainId $explainId, situateId $situateId")
    }

    override fun statementAdded(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        isExplicit: Boolean,
        pluginConnection: PluginConnection,
    ): Boolean {
        logger.debug(
            "statement {} {} {} {} {}",
            subjectId,
            predicateId,
            objectId,
            contextId,
            if (isExplicit) "explicit" else "implicit"
        )
        if (!isExplicit) {
            implicitStatements.add(Quad(subjectId, predicateId, objectId, contextId))
                .also { logger.debug("adding {}", it) }
        }

        logger.debug("number of implicit statements in add: {}", implicitStatements.count())
        return true
    }

    override fun statementRemoved(p0: Long, p1: Long, p2: Long, p3: Long, p4: Boolean, p5: PluginConnection?): Boolean =
        true

    override fun preprocess(request: Request): RequestContext {
        logger.debug("number of implicit statements in preprocess: {}", implicitStatements.count())
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
        pluginConnection: PluginConnection?,
        requestContext: RequestContext?
    ): StatementIterator? {
        if (predicateId != situateId || requestContext !is SituatedInferenceContext || pluginConnection == null) {
            return null
        }
        pluginConnection.entities[objectId].let {
            if (!it.isBNode && !it.isIRI) {
                throw PluginException("${it.stringValue()} in object isn't a valid named graph")
            }
        }

        logger.debug("number of implicit statements to process: {}", implicitStatements.count())

        requestContext.contextsInScope.add(objectId)
        logger.debug("Adding {} to contexts in scope", objectId)
        logger.debug("All contexts in scope {}", requestContext.contextsInScope)

        val implicitStatementsWithAntecedents = implicitStatements.associateWith { implicitStmt ->
            getAntecedentsWithRule(
                implicitStmt.subject,
                implicitStmt.predicate,
                implicitStmt.`object`,
                implicitStmt.context,
                pluginConnection,
                requestContext
            )
        }.also { logger.debug("Antecedents with rules {}", it) }

        val implicitStatementsWithAntecedentsInScope = implicitStatementsWithAntecedents.mapValues { (_, solutions) ->
            solutions.filter { it.areAntecedentsInScope(requestContext.contextsInScope) }
        }

        val (statementsInScope, statementsOutOfScope) = implicitStatementsWithAntecedentsInScope.entries.partition { (_, solutions) ->
            solutions.isNotEmpty()
        }

        logger.debug("statements in scope: {}", statementsInScope)
        logger.debug("statements out of scope: {}", statementsOutOfScope)

        requestContext.repositoryConnection.insertStatementsInScope(statementsInScope)
        requestContext.repositoryConnection.removeStatementsOutOfScope(statementsOutOfScope)

        return StatementIterator.EMPTY
    }

    private fun Solution.areAntecedentsInScope(contexts: Collection<Long>): Boolean {
        return contexts.any { context -> antecedents.all { it.context == context } }
    }

    private fun AbstractRepositoryConnection.insertStatementsInScope(
        statementsInScope: List<Map.Entry<Quad, List<Solution>>>
    ) {
        statementsInScope.forEach { (quad, solutions) ->
            val contextsForQuad =
                solutions.fold(listOf<Long>()) { acc, solution -> acc + solution.antecedents.map { it.context } }
            contextsForQuad.forEach { context ->
                putStatement(
                    quad.subject,
                    quad.predicate,
                    quad.`object`,
                    context,
                    StatementIdIterator.INFERRED_STATEMENT_STATUS or StatementIdIterator.GENERATED_STATEMENT_STATUS
                )
            }
        }
    }

    private fun AbstractRepositoryConnection.removeStatementsOutOfScope(
        statementsOutOfScope: List<Map.Entry<Quad, List<Solution>>>
    ) {
        statementsOutOfScope.forEach { (quad, _) ->
            removeStatements(
                quad.subject, quad.predicate, quad.`object`
            )
        }
    }


    override fun transactionStarted(p0: PluginConnection?) {}

    override fun transactionCommit(p0: PluginConnection?) {}

    override fun transactionCompleted(p0: PluginConnection?) {
//        implicitStatements.clear()
    }

    override fun transactionAborted(p0: PluginConnection?) {
//        implicitStatements.clear()
    }


    private fun getAntecedentsWithRule(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: SituatedInferenceContext
    ): MutableSet<Solution> {
        val reificationId = pluginConnection.entities.put(bnode(), Entities.Scope.REQUEST)
        val quadToExplain = Quad(
            subjectId,
            predicateId,
            objectId,
            contextId,
        )
        val statementProps =
            requestContext.repositoryConnection.getExplicitStatementProps(subjectId, predicateId, objectId)
        return ExplainIter(requestContext, reificationId, 0, quadToExplain, statementProps).solutions
    }


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

//    override fun shouldPostprocess(requestContext: RequestContext?): Boolean =
//        requestContext is SituatedInferenceContext
//
//    override fun postprocess(bindingSet: BindingSet?, requestContext: RequestContext?): BindingSet {
//        logger.debug("POSTPROCESS!!!")
//        if (requestContext !is SituatedInferenceContext) {
//            throw PluginException("Postprocess requestContext should be ${SituatedInferenceContext.Companion::class.java} but is ${requestContext?.javaClass}")
//        }
//
//        return bindingSet ?: EmptyBindingSet()
//    }

//    override fun flush(p0: RequestContext?): MutableIterator<BindingSet> {
////        TODO("Not yet implemented")
//        return
//    }
}

