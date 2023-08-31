package situatedInference

interface Quotable {
    val sourceId: Long
    val quotableId: Long
    val requestContext: SituatedInferenceContext

    fun getQuotingAsSubject(): SimpleContext {
        val statementInSubject =
            requestContext.repositoryConnection.getStatements(sourceId, 0, 0, 0).asSequence().map {
                it.replaceValues(sourceId, quotableId)
            }
        return SimpleContext.fromSequence(statementInSubject)
    }

    fun getQuotingAsObject(): SimpleContext {
        val statementInObject =
            requestContext.repositoryConnection.getStatements(0, 0, sourceId, 0).asSequence().map {
                it.replaceValues(sourceId, quotableId)
            }
        return SimpleContext.fromSequence(statementInObject)
    }
}