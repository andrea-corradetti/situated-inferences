package situatedInference

import com.ontotext.trree.plugin.provenance.MemoryStorage
import com.ontotext.trree.plugin.provenance.Storage


//TODO refactor to Interface and impl
open class ContextWithStorage() : InMemoryContext {
    val storage: Storage = MemoryStorage()

    fun deleteAll() {
        storage.clear()
    }

    fun add(quad: Quad) {
        quad.run { storage.add(subject, predicate, `object`, context, status) }
    }

    open fun add(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long = 0,
        status: Int = 0 //TODO filter by this
    ) {
        storage.add(subjectId, predicateId, objectId, contextId, status)
    }


    fun addAll(collection: Collection<Quad>) {
        collection.forEach { add(it) }
    }

    fun addAll(vararg quads: Quad) {
        quads.forEach { add(it) }
    }

    fun addAll(sequence: Sequence<Quad>) {
        sequence.forEach { add(it) }
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


