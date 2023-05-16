import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.sdk.StatementIterator

class ConjIter: StatementIterator() {

    val inferencer: AbstractInferencer? = null
    val requestContext: NamedInferenceContext? = null
    var connection: AbstractRepositoryConnection? = null
    override fun next(): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}