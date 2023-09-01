package situatedInference

import com.ontotext.trree.plugin.provenance.MemoryStorage
import com.ontotext.trree.plugin.provenance.Storage


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


open class ContextWithStorage() : InMemoryContext {
    val storage: Storage = MemoryStorage()

    fun deleteAll() {
        storage.clear()
    }

    fun add(quad: Quad) {
        quad.run { storage.add(subject, predicate, `object`, context, status) }
    }

    fun addAll(collection: Collection<Quad>) {
        collection.forEach { add(it) }
    }

    fun addAll(vararg quads: Quad) {
        quads.forEach { add(it) }
    }


    override fun find(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        status: Int //TODO filter by this
    ): Sequence<Quad> {
        return storage.find(subjectId, predicateId, objectId, contextId).asSequence()
    }

    override fun getAll(): Sequence<Quad> {
        return find(0, 0, 0)
    }

    companion object {
        fun fromSequence(sequence: Sequence<Quad>): ContextWithStorage {
            return ContextWithStorage().apply {
                addAll(sequence.toList())
            }
        }
    }

}


