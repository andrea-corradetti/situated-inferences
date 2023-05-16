import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.sdk.Request
import com.ontotext.trree.sdk.RequestContext
import org.eclipse.rdf4j.repository.RepositoryConnection

class NamedInferenceContext: RequestContext {
    private var request: Request? = null
    public val attributes = HashMap<String, Any>()
    var repositoryConnection: AbstractRepositoryConnection? = null
    var inferencer: AbstractInferencer? = null
    override fun getRequest(): Request? = request
    override fun setRequest(value: Request?) {
        request = value
    }
}

enum class NamedInferenceContextKey(val key: String) {
    GRAPH_NAMES("graphNames")
}