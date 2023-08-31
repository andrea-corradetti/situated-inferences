package situatedInference

import com.ontotext.trree.sdk.Entities.UNBOUND

data class Singleton(
    val reifiedStatementId: Long,
    val singletonQuad: Quad,
    override val requestContext: SituatedInferenceContext
) : InMemoryContext, Quotable {
    override fun find(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        status: Int
    ): Sequence<Quad> {
        val matches =
            (subjectId == UNBOUND || subjectId == singletonQuad.subject) &&
                    (predicateId == UNBOUND || predicateId == singletonQuad.predicate) &&
                    (objectId == UNBOUND || objectId == singletonQuad.`object`) &&
                    (contextId == UNBOUND || contextId == singletonQuad.context)


        return if (matches) sequenceOf(singletonQuad) else emptySequence()
    }

    override fun getAll(): Sequence<Quad> = sequenceOf(singletonQuad)


    override val sourceId: Long
        get() = reifiedStatementId
    override val quotableId: Long
        get() = singletonQuad.context

}
