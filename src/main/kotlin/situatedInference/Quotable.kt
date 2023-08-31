package situatedInference

interface Quotable {
    val sourceId: Long
    val quotableId: Long

    fun getQuotingAsSubject(): SimpleContext

    fun getQuotingAsObject(): SimpleContext
}
