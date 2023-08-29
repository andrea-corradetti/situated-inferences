package situatedInference

import com.ontotext.trree.plugin.provenance.MemoryStorage

class InMemoryContext(val id: Long, ) {
    var storage = MemoryStorage()

    companion object {
        fun fromSequence(id: Long, sequence: Sequence<Quad>): InMemoryContext {
            return InMemoryContext(id).apply {
                sequence.forEach { storage.add(it.subject, it.predicate, it.`object`, it.context, it.status) }
            }
        }
    }
}