package situatedInference

import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.OwlimSchemaRepository
import com.ontotext.trree.consistency.ConsistencyException
import com.ontotext.trree.sdk.*
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.slf4j.Logger
import java.util.*
import kotlin.io.path.createTempDirectory

class SituatedInferenceContext(
    val inferencer: AbstractInferencer,
    val repositoryConnection: AbstractRepositoryConnection,
    val logger: Logger? = null,
) : RequestContext {
    val statementIdToSingletonId = mutableMapOf<Long, Long>()
    val sourceIdToQuotable = mutableMapOf<Long, Quotable>()

    val sourceIdToReifcation = mutableMapOf<Long, ReifiedContext>()

    val inMemoryContexts = mutableMapOf<Long, InMemoryContext>()

    val situateTasks = mutableMapOf<Long, SituateTask>()
    val explainTasks = mutableMapOf<Long, ExplainTask>()

    val contextToSituatedContexts = mutableMapOf<Long, MutableSet<Long>>()

    val contextToRepository = mutableMapOf<Long, SailRepository>()

    val schemas = mutableMapOf<Long, SchemaForSituate>()

    private val sailParams = mapOf(
        "ruleset" to "owl2-rl",
        "check-for-inconsistencies" to "true",
    )


    val isInferenceEnabled
        get() = inferencer.inferStatementsFlag

    val entities
        get() = repositoryConnection.entityPoolConnection.entities

    private var request: Request? = null

    override fun getRequest(): Request? = request
    override fun setRequest(request: Request) {
        this.request = request
    }

    val allContexts
        get() = inMemoryContexts.keys + repoContexts

    val repoContexts
        get() = repositoryConnection.contextIDs.asSequence().map { it.context }

    val realContextIdToIsConsistent = mutableMapOf<Long, Boolean>()
    val inMemoryContextIdToIsConsistent = mutableMapOf<Long, Boolean>()
    val pairToDisagrees = mutableMapOf<Pair<Long, Long>, Boolean>()


    fun statementsDisagree(statements: Sequence<Quad>): Boolean {
        val repo = createCleanRepositoryWithDefaults()
        try {
            repo.connection.use { conn ->
                return try {
                    statements.forEach {
                        conn.add(it.asStatement(repositoryConnection.entityPoolConnection))
                    }
                    false
                } catch (e: Exception) {
                    isCause(e, ConsistencyException::class) || throw e
                }
            }
        } finally {
            repo.shutDown()
        }
    }

    private fun createCleanRepositoryWithDefaults(name: String = UUID.randomUUID().toString()) =
        createRepository(name, sailParams)

    private fun createRepository(name: String, sailParams: Map<String, String>): SailRepository {
        val sail = OwlimSchemaRepository().apply { setParameters(sailParams) }
        return SailRepository(sail).apply {
            dataDir = tmpFolder.toFile(); init()
        }
    }

    companion object {
        val tmpFolder = createTempDirectory(prefix = "situated-inferences-plugin")
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
): Sequence<Quad> = sequence {
    yieldAll(values.asSequence().map { it.find(subjectId, predicateId, objectId, contextId, status) }.flatten())
    yieldAll(
        values.asSequence().filterIsInstance<Quotable>()
            .map { it.getQuoting().find(subjectId, predicateId, objectId, contextId, status) }.flatten()
    )
    yieldAll(
        values.asSequence().filterIsInstance<Reified>()
            .map { it.getQuotingInnerStatement().find(subjectId, predicateId, objectId, contextId, status) }.flatten()
    )
    yieldAll(
        values.asSequence().filterIsInstance<Expandable>()
            .map { it.getExpansions().find(subjectId, predicateId, objectId, contextId, status) }.flatten()
    )
}

//TODO add exception rethrow
inline fun <R> SailRepository.use(block: (SailRepository) -> R): R {
    return try {
        block(this)
    } finally {
        this.shutDown()
//        this.dataDir.deleteRecursively()
    }
}


