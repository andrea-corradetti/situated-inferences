package situatedInference

data class Singleton(val reifiedStatementId: Long, val singletonQuad: Quad) : InMemoryContext {
    override fun find(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        status: Int
    ): Sequence<Quad> {
        return if (Triple(subjectId, predicateId, objectId) == singletonQuad.asTriple())
            sequenceOf(singletonQuad)
        else
            emptySequence()
    }

    override fun getAll(): Sequence<Quad> = sequenceOf(singletonQuad)
}
