package situatedInference

import com.ontotext.trree.*
import com.ontotext.trree.query.QueryResultIterator
import com.ontotext.trree.query.StatementSource
import org.slf4j.Logger
import kotlin.properties.Delegates


class ExplainIter(
    requestContext: SituatedInferenceContext, //id of bnode representing the explain operation
    val reificationId: Long,
    val explainId: Long,
    private val statementToExplain: Quad,
    explicitStatementProps: ExplicitStatementProps
) : ReportSupportedSolution {

    private val contextResolver: ContextResolver = ContextResolver(requestContext.repositoryConnection)
    private val logger: Logger? = requestContext.logger
    private var inferencer: AbstractInferencer = requestContext.inferencer
    private var repositoryConnection: AbstractRepositoryConnection =
        requestContext.repositoryConnection // connection to the raw data to get only the AXIOM statements ???

    private var isExplicit: Boolean = explicitStatementProps.isExplicit
    private var isDerivedFromSameAs: Boolean = explicitStatementProps.isDerivedFromSameAs
    private var explicitContext: Long = explicitStatementProps.explicitContext //FIXME possibly misguiding name

    var solutions = mutableSetOf<Solution>()

    init {
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
            solutions.add(Solution("explicit", listOf(antecedent)))
        } else {
            inferencer.isSupported(
                statementToExplain.subject, statementToExplain.predicate, statementToExplain.`object`, 0, 0, this
            ) //this method callbacks overridden methods from ReportSupportedSolution on this
        }
    }


    //Triggered by inferencer.isSupported as callback
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
                    }
                }
            }
            queryResultIterator.next()
        }

        val sharedContextsForSolution =
            antecedentToContexts.values.reduceOrNull { acc, set -> acc intersect set } ?: listOf()

        val solutionSets = sharedContextsForSolution.map { sharedContext ->
            antecedentToContexts.keys.map { Quad(it.subject, it.predicate, it.`object`, sharedContext, it.status) }
        }


        solutionSets.forEach {
            logger.debug("solutionSet for {} = {}", ruleName, it)
            val solution = Solution(ruleName, it.distinct())
            val added = solutions.add(solution)
            logger.debug(if (added) "added $solution" else "already added $solution")
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
