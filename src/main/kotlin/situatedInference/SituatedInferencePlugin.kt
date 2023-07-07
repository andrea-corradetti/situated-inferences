package situatedInference

import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.sdk.*
import org.eclipse.rdf4j.model.util.Values.iri
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.impl.EmptyBindingSet
import proof.ProofPlugin.excludeDeletedHiddenInferred
import kotlin.properties.Delegates

class SituatedInferencePlugin : PluginBase(), Preprocessor, StatementListener, PluginTransactionListener,
    PatternInterpreter, Postprocessor {

    private val implicitStatements = mutableListOf<Quad>()

    private val namespace = "https://w3id.org/conjectures/"
    private val explainUri = iri(namespace + "explain")
    private val situateUri = iri(namespace + "situate")
    private var explainId by Delegates.notNull<Long>()
    private var situateId by Delegates.notNull<Long>()

    override fun getName(): String = "Situated-Inference"

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
        pluginConnection: PluginConnection
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

        return true
    }

    override fun statementRemoved(p0: Long, p1: Long, p2: Long, p3: Long, p4: Boolean, p5: PluginConnection?): Boolean =
        true

    override fun preprocess(request: Request): RequestContext {
        return SituatedInferenceContext.fromRequest(request, logger)
    }

    override fun estimate(p0: Long, p1: Long, p2: Long, p3: Long, p4: PluginConnection?, p5: RequestContext?): Double {
        TODO("Not yet implemented")
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
        requestContext.contextsInScope.add(contextId)
        requestContext.repositoryConnection.repository.
        return StatementIterator.EMPTY

    }


    override fun transactionStarted(p0: PluginConnection?) {}

    override fun transactionCommit(p0: PluginConnection?) {}

    override fun transactionCompleted(p0: PluginConnection?) {}

    override fun transactionAborted(p0: PluginConnection?) {}


    private fun PluginConnection.getAntecedents(
        subjectId: Long, predicateId: Long, objectId: Long, contextId: Long
    ): List<LongArray> {
        TODO()
    }


    private fun AbstractRepositoryConnection.getExplicitStatementProps(
        subjToExplain: Long, objToExplain: Long, predToExplain: Long
    ): ExplicitStatementProps {
        var isExplicit: Boolean
        var explicitContext: Long
        var isDerivedFromSameAs: Boolean
        this.use {
            val iterForExplicit = getStatements(
                subjToExplain, objToExplain, predToExplain, excludeDeletedHiddenInferred
            )
            iterForExplicit.use {
                logger.debug("iter getStatements context" + iterForExplicit.context)
                isExplicit = iterForExplicit.hasNext()
                explicitContext = iterForExplicit.context
                isDerivedFromSameAs =
                    iterForExplicit.status and StatementIdIterator.SKIP_ON_REINFER_STATEMENT_STATUS != 0 // handle if explicit comes from sameAs
            }
            return ExplicitStatementProps(isExplicit, explicitContext, isDerivedFromSameAs)
        }
    }

    override fun shouldPostprocess(requestContext: RequestContext?): Boolean =
        requestContext is SituatedInferenceContext

    override fun postprocess(bindingSet: BindingSet?, requestContext: RequestContext?): BindingSet {
        logger.debug("POSTPROCESS!!!")
        if (requestContext !is SituatedInferenceContext) {
            throw PluginException("Postprocess requestContext should be ${SituatedInferenceContext.Companion::class.java} but is ${requestContext?.javaClass}")
        }
        bindingSet ?: return EmptyBindingSet()
        requestContext.repositoryConnection.
    }

    override fun flush(p0: RequestContext?): MutableIterator<BindingSet> {
        TODO("Not yet implemented")
    }
}

