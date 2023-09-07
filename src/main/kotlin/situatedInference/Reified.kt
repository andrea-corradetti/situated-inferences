package situatedInference

interface Reified: Quotable {
    fun getQuotingInnerStatementsAsSubject(): ContextWithStorage

    fun getQuotingInnerStatementsAsObject(): ContextWithStorage

    fun getQuotingInnerStatement(): ContextWithStorage
}