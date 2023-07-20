package situatedInference

import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.ReportSupportedSolution
import com.ontotext.trree.query.QueryResultIterator
import com.ontotext.trree.query.StatementSource
import com.ontotext.trree.sdk.StatementIterator
import org.slf4j.Logger

class ExplainIter(
    requestContext: SituatedInferenceContext, //id of bnode representing the explain operation
    reificationId: Long,
    explainId: Long,
    private val statementToExplain: Quad,
    explicitStatementProps: ExplicitStatementProps
) : StatementIterator(), ReportSupportedSolution {

    private val contextResolver: ContextResolver = ContextResolver(requestContext.repositoryConnection)
    private val logger: Logger? = requestContext.logger
    private var inferencer: AbstractInferencer = requestContext.inferencer
    private var repositoryConnection: AbstractRepositoryConnection =
        requestContext.repositoryConnection // connection to the raw data to get only the AXIOM statements ???

    private var isExplicit: Boolean = explicitStatementProps.isExplicit
    private var isDerivedFromSameAs: Boolean = explicitStatementProps.isDerivedFromSameAs
    private var explicitContext: Long = explicitStatementProps.explicitContext //FIXME possibly misguiding name

    var solutions = mutableSetOf<Solution?>()
    private var iter: Iterator<Solution?>? = null
    private var currentSolution: Solution? = null
    private var currentPremiseNo = -1
    private var values: Quad? = null

    init {
        subject = reificationId
        predicate = explainId
        init()
    }


    //TODO move logic out of constructor
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
            currentSolution = iter.let { if (it?.hasNext() == true) it.next() else null }
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

        val antecedentToContexts = mutableMapOf<Quad, Set<Long>>()

        while (queryResultIterator.hasNext()) {
            if (queryResultIterator is StatementSource) {
                val sourceSolutionIterator = queryResultIterator.solution()
                while (sourceSolutionIterator.hasNext()) {
                    sourceSolutionIterator.next().use { antecedent ->
                        val antecedentQuad = Quad(antecedent)

                        val isSelfReferential = antecedentQuad.isSameTripleAs(statementToExplain)
                        if (isSelfReferential) {
                            logger.debug("skipped - self referential")
                            return@use
                        }

                        logger.debug("Antecedent default context = {}", antecedentQuad.context)
                        val allContextsForAntecedent =
                            contextResolver.getStatementWithAllContexts(antecedentQuad).map { it.context }.toSet()

                        antecedentToContexts[antecedentQuad] = allContextsForAntecedent

                        //TODO if explaining an implicit statement, it will always be empty. quadToExplain.context is -2 and will never match antecedents in graphs.

                        /*    if (statementToExplain.context == SystemGraphs.IMPLICIT_GRAPH.id.toLong()) {
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
                            }*/
//                        logger.debug("Saved antecedents {}", antecedents)
                    }
                }
            }
            queryResultIterator.next()
        }

        val sharedContextsForSolution =
            antecedentToContexts.values.reduceOrNull { acc, set -> acc.intersect(set) } ?: listOf()

        val solutionSets = sharedContextsForSolution.map { sharedContext ->
            antecedentToContexts.keys.map { Quad(it.subject, it.predicate, it.`object`, sharedContext, it.status) }
        }

        solutionSets.forEach {
            logger.debug("solutionSet for {} = {}", ruleName, it)
            val solution = Solution(ruleName, it.distinct())
            val added = solutions.add(solution)
            logger.debug(if (added) "added $solution" else "already added $solution")
        }

//        val areAllAntecedentsImplicit =
//            antecedents.all { quad -> quad.context.toInt() == SystemGraphs.IMPLICIT_GRAPH.id }
//        if (areAllAntecedentsImplicit) {
//            logger.debug("All antecedents are implicit")
//            return false
//        }
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


    override fun getConnection(): AbstractRepositoryConnection = repositoryConnection


    companion object {
        private fun emptySolutionIterator(): Iterator<Solution?> = object : Iterator<Solution?> {
            override fun hasNext(): Boolean {
                return false
            }

            override fun next(): Solution? {
                return null
            }
        }
    }

}
