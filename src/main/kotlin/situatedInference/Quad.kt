package situatedInference

import com.ontotext.trree.StatementIdIterator

data class Quad(
    @JvmField val subject: Long,
    @JvmField val predicate: Long,
    @JvmField val `object`: Long,
    @JvmField val context: Long = 0,
    @JvmField val status: Int = 0,
) {
    constructor(array: LongArray) : this(array[0], array[1], array[2], array[3])
    constructor(iterator: StatementIdIterator) : this(
        iterator.subj,
        iterator.pred,
        iterator.obj,
        iterator.context,
        iterator.status
    )

    fun isSameTripleAs(other: Quad): Boolean =
        (subject == other.subject) && (predicate == other.predicate) && (`object` == other.`object`)

    fun asTriple() = Triple(subject, predicate, `object`)
}


data class Triple(
    @JvmField val subject: Long,
    @JvmField val predicate: Long,
    @JvmField val `object`: Long,
)