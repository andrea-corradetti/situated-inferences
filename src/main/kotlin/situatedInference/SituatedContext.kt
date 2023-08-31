package situatedInference


class SituatedContext(
    val situatedContextId: Long,
    override val sourceId: Long,
    val additionalContexts: Set<Long> = emptySet(),
    override val requestContext: SituatedInferenceContext
) : ContextWithStorage(), Quotable {

    override val quotableId: Long
        get() = situatedContextId
}