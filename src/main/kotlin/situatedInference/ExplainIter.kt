package situatedInference

import com.ontotext.trree.*
import com.ontotext.trree.query.QueryResultIterator
import com.ontotext.trree.query.StatementSource
import com.ontotext.trree.sdk.StatementIterator
import org.slf4j.Logger
import proof.ProofPlugin

class ExplainIter(
    requestContext: SituatedInferenceContext, //id of bnode representing the explain operation
    private var reificationId: Long,
    explainId: Long,
    statementToExplain: Quad,
    explicitStatementProps: ExplicitStatementProps
) : StatementIterator(), ReportSupportedSolution {
    private val logger: Logger?
    private val statementToExplain: Quad
    private val contextResolver: ContextResolver
    private var inferencer: AbstractInferencer
    private var repositoryConnection // connection to the raw data to get only the AXIOM statements ???
            : AbstractRepositoryConnection
    private var isExplicit: Boolean
    private var isDerivedFromSameAs: Boolean
    private var explicitContext //FIXME possibly misguiding name
            : Long
    var solutions = mutableSetOf<Solution?>()
    private var iter: Iterator<Solution?>? = null
    private var currentSolution: Solution? = null
    private var currentPremiseNo = -1
    private var values: Quad? = null

    init {
        subject = reificationId
        predicate = explainId
        this.statementToExplain = statementToExplain
        isExplicit = explicitStatementProps.isExplicit
        isDerivedFromSameAs = explicitStatementProps.isDerivedFromSameAs
        explicitContext = explicitStatementProps.explicitContext
        inferencer = requestContext.inferencer
        repositoryConnection = requestContext.repositoryConnection
        logger = requestContext.logger
        contextResolver = ContextResolver(repositoryConnection)
        init()
    }

    private fun init() {
        if (isExplicit) {
            val antecedent = Quad(
                statementToExplain.subject,
                statementToExplain.predicate,
                statementToExplain.`object`,
                explicitContext,
                0
            )
            currentSolution = Solution("explicit", listOf(antecedent))
            currentPremiseNo = 0
            iter = emptySolutionIterator()
        } else {
            inferencer.isSupported(
                statementToExplain.subject, statementToExplain.predicate, statementToExplain.`object`, 0, 0, this
            ) //this method has a side effect that prompts the overridden report() method to populate our solution with antecedents. The strange this reference does exactly that. Sorry, I don't make the rules. No idea what the fourth parameter does. Good luck
            iter = solutions.iterator()
            if (iter!!.hasNext()) {
                currentSolution = iter!!.next()
            }
            if (currentSolution != null) {
                currentPremiseNo = 0
            }
        }
    }

    override fun report(ruleName: String, queryResultIterator: QueryResultIterator): Boolean {
        logger!!.debug(
            "report rule {} for {},{},{}",
            ruleName,
            statementToExplain.subject,
            statementToExplain.predicate,
            statementToExplain.`object`
        )
        val antecedents: MutableSet<Quad> = HashSet()
        val solutionSets: MutableList<Set<Quad>> = ArrayList()
        while (queryResultIterator.hasNext()) {
            if (queryResultIterator is StatementSource) {
                val sourceSolutionIterator: Iterator<StatementIdIterator> = queryResultIterator.solution()
                while (sourceSolutionIterator.hasNext()) {
                    sourceSolutionIterator.next().use { antecedent ->
                        val isSelfReferential =
                            antecedent.subj == statementToExplain.subject && antecedent.pred == statementToExplain.predicate && antecedent.obj == statementToExplain.`object`
                        if (isSelfReferential) {
                            logger.debug("skipped - self referential")
                            return@use
                        }
                        logger.debug("Antecedent default context = {}", antecedent.context)
                        val antecedentWithAllContexts = contextResolver.getStatementWithAllContexts(
                            Quad(
                                antecedent.subj, antecedent.pred, antecedent.obj, antecedent.context, antecedent.status
                            )
                        )
                        logger.debug("antecedents with all contexts {}", antecedentWithAllContexts)
                        solutionSets.add(antecedentWithAllContexts)

                        //TODO if explaining an implicit statement, it will always be empty. quadToExplain.context is -2 and will never match antecedents in graphs.
                        if (statementToExplain.context == SystemGraphs.IMPLICIT_GRAPH.id.toLong()) {
                        }
                        val isStatementInSameContext = antecedentWithAllContexts.stream()
                            .anyMatch { (_, _, _, context1): Quad -> context1 == statementToExplain.context }
                        if (isStatementInSameContext) {
                            logger.debug("statement is same context {}", statementToExplain.context)
                            antecedents.add(
                                Quad(
                                    antecedent.subj,
                                    antecedent.pred,
                                    antecedent.obj,
                                    statementToExplain.context,
                                    antecedent.status
                                )
                            )
                        }
                        val isStatementInDefaultGraph = !isStatementInSameContext && antecedentWithAllContexts.stream()
                            .anyMatch { (_, _, _, context1): Quad -> context1 == SystemGraphs.EXPLICIT_GRAPH.id.toLong() }
                        if (ProofPlugin.isSharedKnowledgeInDefaultGraph && isStatementInDefaultGraph) {
                            logger.debug("statement is in default graph")
                            antecedents.add(
                                Quad(
                                    antecedent.subj,
                                    antecedent.pred,
                                    antecedent.obj,
                                    SystemGraphs.EXPLICIT_GRAPH.id.toLong(),
                                    antecedent.status
                                )
                            )
                        }
                        val isStatementInScope =
                            isStatementInSameContext || ProofPlugin.isSharedKnowledgeInDefaultGraph && isStatementInDefaultGraph
                        val isStatementOnlyImplicit = !isStatementInScope && antecedentWithAllContexts.stream()
                            .allMatch { (_, _, _, context1): Quad -> context1 == SystemGraphs.IMPLICIT_GRAPH.id.toLong() }
                        if (isStatementOnlyImplicit) {
                            logger.debug("statement is only implicit")
                            antecedents.add(
                                Quad(
                                    antecedent.subj,
                                    antecedent.pred,
                                    antecedent.obj,
                                    SystemGraphs.IMPLICIT_GRAPH.id.toLong(),
                                    antecedent.status
                                )
                            )
                        }
                        if (!isStatementInScope && !isStatementOnlyImplicit) {
                            logger.debug(
                                "statement {},{},{} is out of scope", antecedent.subj, antecedent.pred, antecedent.obj
                            )
                            return false
                        }
                        logger.debug("Saved antecedents {}", antecedents)
                    }
                }
            }
            queryResultIterator.next()
        }
        val areAllAntecedentsImplicit = antecedents.all {quad -> quad.context.toInt() == SystemGraphs.IMPLICIT_GRAPH.id }
        if (areAllAntecedentsImplicit) {
            logger.debug("All antecedents are implicit");
            return false;
        }

        val solution = Solution(ruleName, antecedents.toList());
        val added = solutions.add(solution)
        logger.debug(if (added ) "added" else "already added");
        return false
    }

    override fun close() {
        currentSolution = null
        solutions.clear()
    }

    override fun next(): Boolean {
        while (currentSolution != null) {
            if (currentPremiseNo < currentSolution!!.antecedents.size) {
                values = currentSolution!!.antecedents[currentPremiseNo++]
                return true
            }
            values = null
            currentPremiseNo = 0
            currentSolution = if (iter!!.hasNext()) iter!!.next() else null
        }
        return false
    }

    override fun getConnection(): AbstractRepositoryConnection {
        return repositoryConnection
    }

    companion object {
        private fun emptySolutionIterator(): Iterator<Solution?> =
            object : Iterator<Solution?> {
                override fun hasNext(): Boolean {
                    return false
                }

                override fun next(): Solution? {
                    return null
                }
            }
    }
}
