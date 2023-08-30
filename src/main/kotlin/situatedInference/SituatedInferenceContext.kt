package situatedInference

import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.sdk.*
import org.slf4j.Logger
import kotlin.properties.Delegates

class SituatedInferenceContext(
    val inferencer: AbstractInferencer,
    val repositoryConnection: AbstractRepositoryConnection,
    val logger: Logger? = null,
) : RequestContext {


    var sharedScope by Delegates.notNull<Long>()
//    val singletons = mutableMapOf<Long, Singleton>()

    val inMemoryContexts = mutableMapOf<Long, InMemoryContext>()

//    val situations = mutableMapOf<Long, Situation>()

    val situateTasks = mutableMapOf<Long, SituateTask>()
    val explainTasks = mutableMapOf<Long, ExplainTask>()

    val schemas = mutableMapOf<Long, SchemaForSituate>()


    val isInferenceEnabled
        get() = inferencer.inferStatementsFlag


    private var request: Request? = null
    override fun getRequest(): Request? = request
    override fun setRequest(request: Request) {
        this.request = request
    }

    companion object {
        fun fromRequest(request: Request, logger: Logger? = null): SituatedInferenceContext {

            println("dataset ${(request as QueryRequest).dataset}")

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

fun MutableMap<Long, InMemoryContext>.findInAll(
    subjectId: Long,
    predicateId: Long,
    objectId: Long,
    contextId: Long = 0,
    status: Int = 0
): Sequence<Quad> {
    return values.asSequence().map { it.find(subjectId, predicateId, objectId, contextId, status) }.flatten()
}


class InMemoryContextsMap() : MutableMap<Long, InMemoryContext> by mutableMapOf() {
    fun findInAll(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long = 0,
        status: Int = 0
    ): Sequence<Quad> {
        return values.asSequence().map { it.find(subjectId, predicateId, objectId, contextId, status) }.flatten()
    }
}