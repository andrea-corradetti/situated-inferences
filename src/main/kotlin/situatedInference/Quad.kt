package situatedInference

import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.StatementIdIterator.*

data class Quad(
    @JvmField val subject: Long,
    @JvmField val predicate: Long,
    @JvmField val `object`: Long,
    @JvmField val context: Long = 0,
    @JvmField val status: Int = 0, //FIXME inconsistent between StatementIterator and StatementIdIterator. Consider subclassing
) {
    constructor(array: LongArray) : this(
        array[0],
        array[1],
        array[2],
        array[3],
        if (array.size > 4) array[4].toInt() else 0
    )

    constructor(iterator: StatementIdIterator) : this(
        iterator.subj,
        iterator.pred,
        iterator.obj,
        iterator.context,
        iterator.status
    )

    fun isExplicit(): Boolean = status and EXPLICIT_STATEMENT_STATUS != 0

    fun isAxiom(): Boolean = status and AXIOM_STATEMENT_STATUS != 0

    fun isSystemStatement(): Boolean = status and SYSTEM_STATEMENT_STATUS != 0

    fun isSameTripleAs(other: Quad): Boolean =
        (subject == other.subject) && (predicate == other.predicate) && (`object` == other.`object`)

    fun asTriple() = Triple(subject, predicate, `object`)
}


data class Triple(
    @JvmField val subject: Long,
    @JvmField val predicate: Long,
    @JvmField val `object`: Long,
)