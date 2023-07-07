package situatedInference

import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.sdk.PluginException
import com.ontotext.trree.sdk.Request
import com.ontotext.trree.sdk.RequestContext
import com.ontotext.trree.sdk.SystemPluginOptions
import org.slf4j.Logger

class SituatedInferenceContext(
    val inferencer: AbstractInferencer,
    val repositoryConnection: AbstractRepositoryConnection,
    val logger: Logger? = null,
) : RequestContext {

    val contextsInScope = mutableSetOf<Long>()

    private var request: Request? = null
    override fun getRequest(): Request? = request
    override fun setRequest(request: Request) {
        this.request = request
    }

    companion object {
        fun fromRequest(request: Request, logger: Logger? = null): SituatedInferenceContext {
            val options =
                request.options as? SystemPluginOptions ?: throw PluginException("SystemPluginOptions are null")
            val inferencer = options.getOption(SystemPluginOptions.Option.ACCESS_INFERENCER) as? AbstractInferencer
                ?: throw PluginException("Inferencer is null. Can't initialize")
            val repositoryConnection =
                options.getOption(SystemPluginOptions.Option.ACCESS_REPOSITORY_CONNECTION) as? AbstractRepositoryConnection
                    ?: throw PluginException("RepositoryConnection is null. Can't initialize")

            return SituatedInferenceContext(inferencer, repositoryConnection, logger).apply {
                setRequest(request)
            }
        }
    }
}