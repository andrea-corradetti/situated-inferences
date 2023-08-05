package situatedInference

import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.StatementIdIterator.*
import org.slf4j.LoggerFactory

const val excludeDeletedHiddenInferred = DELETED_STATEMENT_STATUS or SKIP_ON_BROWSE_STATEMENT_STATUS or INFERRED_STATEMENT_STATUS

class ContextResolver(private val repositoryConnection: AbstractRepositoryConnection) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    fun getStatementWithAllContexts(statement: Quad): Set<Quad> {
        val statementWithAllContexts = mutableSetOf(statement)
        repositoryConnection.getStatements(
                statement.subject,
                statement.predicate,
                statement.`object`,
                true,
                0,
                excludeDeletedHiddenInferred
        ).use { ctxIter ->
            logger.debug("Contexts for {} {} {}", statement.subject, statement.predicate, statement.`object`)

            while (ctxIter.hasNext()) {
                statementWithAllContexts.add(Quad(ctxIter))
                logger.debug(ctxIter.context.toString())
                ctxIter.next()
            }

            logger.debug("All contexts for antecedent {}", statementWithAllContexts)
        }
        return statementWithAllContexts
    }

}