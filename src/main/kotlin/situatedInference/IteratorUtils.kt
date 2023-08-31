package situatedInference

import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.query.QueryResultIterator
import com.ontotext.trree.query.StatementSource
import com.ontotext.trree.sdk.StatementIterator

fun QueryResultIterator.asSequence() = sequence<StatementIdIterator> {
    while (this@asSequence.hasNext()) {
        if (this@asSequence !is StatementSource) {
            continue
        }
        this@asSequence.solution().forEach { yield(it) }
    }
    this@asSequence.close()
}

fun StatementIterator.asSequence() = sequence {
    while (next()) {
        yield(Quad(subject, predicate, `object`, context))
    }
    this@asSequence.close()
}.constrainOnce()


fun StatementIdIterator.asSequence() = sequence {
    while (hasNext()) {
        yield(Quad(subj, pred, obj, context, status))
        next()
    }
    this@asSequence.close()
}.constrainOnce()

fun statementIteratorFromSequence(sequence: Sequence<Quad>) = object : StatementIterator() {
    val iterator = sequence.iterator()

    override fun next(): Boolean {
        if (iterator.hasNext()) {
            val current = iterator.next()
            this.subject = current.subject
            this.predicate = current.predicate
            this.`object` = current.`object`
            this.context = current.context
            return true
        }
        return false
    }

    override fun close() {}

}

fun StatementIdIterator.toStatementIterator(): StatementIterator {
    return object : StatementIterator() {
        override fun next(): Boolean {
            if (this@toStatementIterator.hasNext()) {
                subject = this@toStatementIterator.subj
                predicate = this@toStatementIterator.pred
                `object` = this@toStatementIterator.obj
                context = this@toStatementIterator.context
                this@toStatementIterator.next()
                return true
            }
            return false

        }

        override fun close() {
            this@toStatementIterator.close()
        }
    }

}

fun Sequence<Quad>.toStatementIterator(): StatementIterator = statementIteratorFromSequence(this)

fun statementIdIteratorFromSequence(statements: Sequence<Quad>) = object : StatementIdIterator() {
    val iterator = statements.iterator()

    init {
        next()
    }

    override fun next() {
        if (iterator.hasNext()) {
            val quad = iterator.next()
            subj = quad.subject
            pred = quad.predicate
            obj = quad.`object`
            context = quad.context
            status = quad.status
            found = true
        } else {
            found = false
        }
    }

    override fun close() {}
    override fun changeStatus(p0: Int) {}
}