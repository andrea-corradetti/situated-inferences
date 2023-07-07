package situatedInference

import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.junit.*


class TestProofWithOwl2RL {
    @Before
    fun removeAllTriples() {
        connection.prepareUpdate(deleteLessie).execute()
    }

    @Test
    fun `statement add is captured`() {
        connection.prepareUpdate(insertSituate).execute()
    }

    companion object {
        private lateinit var repository: SailRepository
        private lateinit var connection: SailRepositoryConnection

        @JvmField
        @ClassRule
        val tmpFolder = TemporaryLocalFolder()

        @JvmStatic
        @BeforeClass
        fun setUp() {
            setWorkDir()
            val sailParams = mapOf(
                "register-plugins" to SituatedInferencePlugin::class.qualifiedName as String,
                "ruleset" to "owl2-rl",
            )
            repository = getRepository(sailParams)
            connection = repository.connection
        }

        @JvmStatic
        fun setWorkDir() {
            System.setProperty("graphdb.home.work", "${tmpFolder.root}")
            Config.reset()
        }

        private fun getRepository(sailParams: Map<String, String>): SailRepository {
            val sail = OwlimSchemaRepository().apply { setParameters(sailParams) }
            return SailRepository(sail).apply {
                dataDir = tmpFolder.newFolder("proof-plugin-explain-${sailParams["ruleset"]}"); init()
            }
        }

        @JvmStatic
        @AfterClass
        fun cleanUp() {
            resetWorkDir()
            connection.close()
            repository.shutDown()
        }

        @JvmStatic
        fun resetWorkDir() {
            System.clearProperty("graphdb.home.work")
            Config.reset()
        }
    }
}