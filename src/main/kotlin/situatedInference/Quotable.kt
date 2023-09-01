package situatedInference

interface Quotable {
    val sourceId: Long
    val quotableId: Long

    fun getQuotingAsSubject(): ContextWithStorage

    fun getQuotingAsObject(): ContextWithStorage

    fun getQuoting(): ContextWithStorage
}


class QuotableImpl(
    override val sourceId: Long,
    override val quotableId: Long,
    private val requestContext: SituatedInferenceContext
) : Quotable {

    override fun getQuotingAsSubject(): ContextWithStorage {
        val statementInSubject =
            requestContext.repositoryConnection.getStatements(sourceId, 0, 0, 0).asSequence().map {
                it.replaceValues(sourceId, quotableId)
            }
        return ContextWithStorage.fromSequence(statementInSubject)
    }

    override fun getQuotingAsObject(): ContextWithStorage {
        val statementInObject =
            requestContext.repositoryConnection.getStatements(0, 0, sourceId, 0).asSequence().map {
                it.replaceValues(sourceId, quotableId)
            }
        return ContextWithStorage.fromSequence(statementInObject)
    }

    override fun getQuoting(): ContextWithStorage {
        val statementInSubject =
            requestContext.repositoryConnection.getStatements(sourceId, 0, 0, 0).asSequence().map {
                it.replaceValues(sourceId, quotableId)
            }

        val statementInObject =
            requestContext.repositoryConnection.getStatements(0, 0, sourceId, 0).asSequence().map {
                it.replaceValues(sourceId, quotableId)
            }

        return ContextWithStorage.fromSequence((statementInSubject + statementInObject).distinct())
    }
}

interface Reified: Quotable {
    fun getQuotingInnerStatementsAsSubject(): ContextWithStorage

    fun getQuotingInnerStatementsAsObject(): ContextWithStorage

    fun getQuotingInnerStatement(): ContextWithStorage
}