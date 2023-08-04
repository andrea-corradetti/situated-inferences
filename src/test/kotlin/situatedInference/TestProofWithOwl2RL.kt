package situatedInference

import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.junit.*
import kotlin.test.assertEquals


class TestProofWithOwl2RL {
    @Before
    fun removeAllTriples() {
        connection.prepareUpdate(deleteLessie).execute()
    }

    @Test
    fun `Mary in same graphs is situated correctly`() {
        connection.prepareUpdate(addMaryInSameGraph).execute()
        connection.prepareTupleQuery(situateMary).evaluate().use { result ->
            val resultList = result.toList()
            println("result for situate:")
            val message = resultList.joinToString("\n") { bindingSet ->
                bindingSet.joinToString(" ") { "${it.name}: ${it.value}" }
            }
            print(message)
            assertEquals(10, resultList.count(), "Results situated correctly")
        }
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
//                "ruleset" to "owl-horst",
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

internal val addMaryInDifferentGraphs = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    INSERT DATA {
        <urn:childOf> owl:inverseOf <urn:hasChild> .
        graph <urn:family> {
            <urn:John> <urn:childOf> <urn:Mary>.
        }
    }
""".trimIndent()

internal val addMaryInSameGraph = """
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    INSERT DATA {
        graph <urn:family> {
        	<urn:childOf> owl:inverseOf <urn:hasChild> .
            <urn:John> <urn:childOf> <urn:Mary>.
        }
    }
""".trimIndent()


internal val situateMary = """
    PREFIX conj: <https://w3id.org/conjectures/>
    
    select ?s ?p ?o where {
        conj:family conj:situate (<urn:family>)  .
        
        graph conj:family {
            ?s ?p ?o 
        }
    }
""".trimIndent()


internal val situateG1 = """
    PREFIX conj: <https://w3id.org/conjectures/>
    PREFIX : <http://www.example.com/>
    
    SELECT * {
        [] conj:situate :G2.
        
        GRAPH :G2 {
            ?s ?p ?o
        }
    }
""".trimIndent()

internal val insertLassieNg = """
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX : <http://www.example.com/>
    PREFIX t: <http://www.example.com/tbox/>
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    
    INSERT DATA {
        graph :G1 {
            :Lassie rdf:type :Dog.        
        }
        
        graph :G2 {
            :Lassie rdf:type :Dog.       
            :Dog rdfs:subClassOf :Mammal.
        }
    }
""".trimIndent()


