import com.ontotext.trree.sdk.Request
import com.ontotext.trree.sdk.RequestContext

class Context: RequestContext {
    private var request: Request? = null
    public val attributes = HashMap<String, Any>()
    override fun getRequest(): Request? = request
    override fun setRequest(value: Request?) {
        request = value
    }
}