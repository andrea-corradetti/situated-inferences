package situatedInference

interface Quotable {
    val sourceId: Long
    val quotableId: Long

    fun getQuotingAsSubject(): SimpleContext

    fun getQuotingAsObject(): SimpleContext
}


class QuotableImpl(
    override val sourceId: Long,
    override val quotableId: Long,
    private val requestContext: SituatedInferenceContext
) : Quotable {

    override fun getQuotingAsSubject(): SimpleContext {
        val statementInSubject =
            requestContext.repositoryConnection.getStatements(sourceId, 0, 0, 0).asSequence().map {
                it.replaceValues(sourceId, quotableId)
            }
        return SimpleContext.fromSequence(statementInSubject)
    }

    override fun getQuotingAsObject(): SimpleContext {
        val statementInObject =
            requestContext.repositoryConnection.getStatements(0, 0, sourceId, 0).asSequence().map {
                it.replaceValues(sourceId, quotableId)
            }
        return SimpleContext.fromSequence(statementInObject)
    }

}