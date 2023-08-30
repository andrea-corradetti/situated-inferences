package situatedInference

import com.ontotext.trree.StatementIdIterator

class StatementStorage {
    private val subjectMap = mutableMapOf<Long, MutableSet<Quad>>()
    private val predicateMap = mutableMapOf<Long, MutableSet<Quad>>()
    private val objectMap = mutableMapOf<Long, MutableSet<Quad>>()
    private val contextMap = mutableMapOf<Long, MutableSet<Quad>>()

    fun find(subject: Long, predicate: Long, `object`: Long, context: Long): Sequence<Quad> {
        TODO()
    }
}