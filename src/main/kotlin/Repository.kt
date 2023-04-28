import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.config.RepositoryConfig
import org.eclipse.rdf4j.repository.config.RepositoryImplConfig
import org.eclipse.rdf4j.repository.manager.LocalRepositoryManager
import org.eclipse.rdf4j.repository.sail.config.SailRepositoryConfig
import org.eclipse.rdf4j.sail.config.SailImplConfig
import org.eclipse.rdf4j.sail.inferencer.fc.config.SchemaCachingRDFSInferencerConfig
import org.eclipse.rdf4j.sail.memory.config.MemoryStoreConfig
import java.io.File


object repoStore {
    private val baseDir = File(".")
    private val manager = LocalRepositoryManager(baseDir)
//    private val graph = TreeModel()

    init {
        manager.init()
    }

//    fun readConfig(configPath: Path) {
//        javaClass.getResourceAsStream(configPath.toString()).use { configInputStream ->
//            val rdfParser = Rio.createParser(RDFFormat.TURTLE)
//            rdfParser.setRDFHandler(StatementCollector(graph));
//            rdfParser.parse(configInputStream, RepositoryConfigSchema.NAMESPACE)
//        }
//    }
//
//    fun retrieveRepositoryAsResource(): Resource {
//        val statements = graph.getStatements(null, RDF.TYPE, RepositoryConfigSchema.REPOSITORY)
//        return statements.first().subject
//    }
//
//    fun addConfiguration(repositoryNode: Resource) {
//        manager.addRepositoryConfig(RepositoryConfig.create(graph, repositoryNode))
//    }

    fun getRepositoryForContext(context: Resource): Repository {
        val repositoryId: String = getRepositoryIdFromContext(context)
        if (manager.getRepositoryConfig(repositoryId) == null) {
            addConfigForRepository(repositoryId)
        }
        return manager.getRepository(repositoryId)
    }

    private fun addConfigForRepository(repositoryId: String) {
        val repositoryTypeSpec: RepositoryImplConfig = getRepositoryTypeSpec(repositoryId)
        val repConfig = RepositoryConfig(repositoryId, repositoryTypeSpec)
        manager.addRepositoryConfig(repConfig)
    }

    private fun getRepositoryTypeSpec(repositoryId: String): RepositoryImplConfig {
        val persist = true
        var backendConfig: SailImplConfig = MemoryStoreConfig(persist)
        // stack an inferencer config on top of our backend-config
        backendConfig = SchemaCachingRDFSInferencerConfig(backendConfig)
        // create a configuration for the repository implementation
        val repositoryTypeSpec = SailRepositoryConfig(backendConfig)
        return repositoryTypeSpec
    }

    private fun getRepositoryIdFromContext(context: Resource) = "context-${context.stringValue()}"

}

