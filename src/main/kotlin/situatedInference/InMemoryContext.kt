package situatedInference

interface InMemoryContext {

//    fun find(quad: Quad): Sequence<Quad>

    fun find(subjectId: Long, predicateId: Long, objectId: Long, contextId: Long = 0, status: Int = 0): Sequence<Quad>

//    fun contains(quad: Quad): Boolean {
//        return find(quad).any()
//    }

    fun contains(subjectId: Long, predicateId: Long, objectId: Long, contextId: Long = 0): Boolean {
        return find(subjectId, predicateId, objectId, contextId).any()
    }

    fun getAll(): Sequence<Quad>

}