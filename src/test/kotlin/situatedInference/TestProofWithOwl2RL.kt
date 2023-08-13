package situatedInference

import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.util.*


class TestProofWithOwl2RL {
    @Test
    fun `Mary in same graphs is situated correctly`() {
        val addToSameGraph = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            INSERT DATA {
                graph <urn:family> {
                    <urn:childOf> owl:inverseOf <urn:hasChild> .
                    <urn:John> <urn:childOf> <urn:Mary>.
                }
            }
        """.trimIndent()
        val situateNamedGraph = """
            PREFIX conj: <https://w3id.org/conjectures/>
            
            select distinct ?s ?p ?o where {
                conj:situation conj:situate (<urn:family>) .
                
                graph conj:situation {
                    ?s ?p ?o .
                }
            }
        """.trimIndent()
        val addToDifferentGraphs = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                <urn:childOf> owl:inverseOf <urn:hasChild> .
                
                graph <urn:family> {
                    <urn:John> <urn:childOf> <urn:Mary>.
                }
            }
        """.trimIndent()
        val situateBothGraphs = """
            PREFIX conj: <https://w3id.org/conjectures/>
            
            select distinct ?s ?p ?o where {
                conj:situation conj:situate ( <http://rdf4j.org/schema/rdf4j#nil> <urn:family> )  .
                
                graph conj:situation {
                    ?s ?p ?o .
                }
            }
        """.trimIndent()
        val addToDefaultGraph = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                <urn:childOf> owl:inverseOf <urn:hasChild> .
                <urn:John> <urn:childOf> <urn:Mary>.
            }
        """.trimIndent()
        val situateDefaultGraph = """
            PREFIX conj: <https://w3id.org/conjectures/>
            
            select distinct ?s ?p ?o where {
                conj:situation conj:situate (<http://rdf4j.org/schema/rdf4j#nil>)  .
                
                graph conj:situation {
                    ?s ?p ?o 
                }
            }
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addToSameGraph).execute()
                it.prepareTupleQuery(situateNamedGraph).evaluate().mapTo(HashSet(), BindingSet::toStringValueMap)
            }
        }

        val result2 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addToDifferentGraphs).execute()
                it.prepareTupleQuery(situateBothGraphs).evaluate().mapTo(HashSet(), BindingSet::toStringValueMap)
            }
        }

        val result3 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addToDefaultGraph).execute()
                it.prepareTupleQuery(situateDefaultGraph).evaluate().mapTo(HashSet(), BindingSet::toStringValueMap)
            }
        }

        assert(result1 == result2) {
            println("result1 and result2 are different: ${result1 symmetricDifference result2} ")
        }

        assert(result2 == result3) {
            println("result2 and result3 are different: ${result2 symmetricDifference result3} ")
        }
    }


    @Test
    fun `Inconsistencies are correctly identified`() {
        val inconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
        
            INSERT DATA {
        
            #######################################
            #                                     #
            #           SHARED KNOWLEDGE          #
            #                                     #
            #######################################
        
                    :Superman a t:Person.
                    :ClarkKent a t:Person.
        
                    :LoisLane a t:Person.
                    :MarthaKent a t:Person.
                    :I a t:Person.
                           
                    t:FictionalPerson rdfs:subClassOf t:Person.
                    t:RealPerson rdfs:subClassOf t:Person.
                    t:FictionalPerson owl:complementOf t:RealPerson.
                   
                    :fly a t:flyingPower.
                    :clingFromCeiling a t:spiderLikePower.
        
                    t:flyingPower rdfs:subClassOf t:supernaturalPower.
                    t:spiderLikePower rdfs:subClassOf t:supernaturalPower.
                   
                    t:SuperHero rdfs:subClassOf t:Person;
                                   owl:onProperty :can;
                                   owl:someValuesFrom t:supernaturalPower.
                    t:FlyingSuperHero rdfs:subClassOf t:SuperHero;
                                   owl:onProperty :can;
                                   owl:someValuesFrom t:flyingPower.   
                    t:SpiderSuperHero rdfs:subClassOf t:SuperHero;
                                   owl:onProperty :can;
                                   owl:someValuesFrom t:spiderLikePower.
                    t:FlyingSuperHero owl:disjointWith t:SpiderSuperHero .
                   
        
        
            #########################################
            #                                       #
            #            NAMED GRAPHS               #
            #                                       #
            #    This should NEVER be consistent    #
            #                                       #
            #########################################
        
                   :LoisLane :thinks :LoisLanesThoughts
                   GRAPH :LoisLanesThoughts {
                        :Superman :can :fly .
                       :Superman owl:differentFrom :ClarkKent.
                        :Superman a t:RealPerson .
                    }
        
                   :MarthaKent :thinks :MarthaKentsThoughts
                   GRAPH :MarthaKentsThoughts {
                        :Superman :can :fly .
                       :Superman owl:sameAs :ClarkKent.
                        :Superman a t:RealPerson .
                    }
        
                   :I :thinks :myThoughts
                   GRAPH :myThoughts {
                        :Superman :can :clingFromCeiling .
                       :Superman owl:sameAs :ClarkKent.
                        :Superman a t:FictionalPerson .
                    }
            }
        """.trimIndent()

        val situateInconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
        
            
            select ?s ?p ?o where {
                conj:thoughts conj:situate ( :LoisLanesThoughts :MarthaKentsThoughts :myThoughts )  .
                
                graph conj:thoughts {
                    ?s ?p ?o 
                }
            }
        """.trimIndent()

    }


    @Test
    fun `Default graph is situated correctly`() {
        val addToDifferentGraphs = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                <urn:childOf> owl:inverseOf <urn:hasChild> .
                
                graph <urn:family> {
                    <urn:John> <urn:childOf> <urn:Mary>.
                }
            }
        """.trimIndent()
        val situateDefaultGraph = """
            PREFIX conj: <https://w3id.org/conjectures/>
            
            select distinct ?s ?p ?o where {
                conj:defaultGraph conj:situate (<http://rdf4j.org/schema/rdf4j#nil>)  .
                
                graph conj:defaultGraph {
                    ?s ?p ?o 
                }
            }
        """.trimIndent()
        val addToDefaultGraph = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                <urn:childOf> owl:inverseOf <urn:hasChild> .
            }
        """.trimIndent()
        val selectAllFromDefault = """
            PREFIX onto: <http://www.ontotext.com/>
            
            select distinct * 
            from onto:readwrite
            where { 
                ?s ?p ?o .
            }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addToDifferentGraphs).execute()
                it.prepareTupleQuery(situateDefaultGraph).evaluate().mapTo(HashSet(), BindingSet::toStringValueMap)
            }
        }

        val result2 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addToDefaultGraph).execute()
                it.prepareTupleQuery(selectAllFromDefault).evaluate().mapTo(HashSet(), BindingSet::toStringValueMap)
            }
        }

        println("List 1 $result1")
        println("List 2 $result2")

        assert(result1 == result2)
    }

    @Test
    fun `Statements in shared contexts are correctly used for inference`() {
        val addToDifferentGraphs = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                <urn:childOf> owl:inverseOf <urn:hasChild> .
                
                graph <urn:family> {
                    <urn:John> <urn:childOf> <urn:Mary>.
                }
            }
        """.trimIndent()
        val situateWithSharedContexts = """
            PREFIX conj: <https://w3id.org/conjectures/>
            
            select distinct ?s ?p ?o where {
                conj:shared conj:situate <http://rdf4j.org/schema/rdf4j#nil>  .
                conj:situation conj:situate <urn:family>
                
                graph conj:situation {
                    ?s ?p ?o 
                }
            }
        """.trimIndent()

        val addToDefaultGraph = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            
            INSERT DATA {
                <urn:childOf> owl:inverseOf <urn:hasChild> .
                <urn:John> <urn:childOf> <urn:Mary> .
            }
        """.trimIndent()

        val selectAllFromDefault = """
            PREFIX onto: <http://www.ontotext.com/>
            
            select distinct * 
            from onto:readwrite
            where { 
                ?s ?p ?o .
            }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addToDifferentGraphs).execute()
                it.prepareTupleQuery(situateWithSharedContexts).evaluate()
                    .mapTo(HashSet(), BindingSet::toStringValueMap)
            }
        }

        val result2 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addToDefaultGraph).execute()
                it.prepareTupleQuery(selectAllFromDefault).evaluate().mapTo(HashSet(), BindingSet::toStringValueMap)
            }
        }

        assert(result1 == result2) {
            println("result1 and result2 are different: ${result1 symmetricDifference result2} ")
        }
    }

    companion object {
        @JvmField
        @ClassRule
        val tmpFolder = TemporaryLocalFolder()

        private val sailParams = mapOf(
            "register-plugins" to SituatedInferencePlugin::class.qualifiedName as String,
//                "ruleset" to "owl2-rl",
//                "ruleset" to "owl-horst",
            "ruleset" to "owl2-ql",
        )


        private fun createCleanRepositoryWithDefaults(name: String = UUID.randomUUID().toString()) =
            createRepository(name, sailParams)

        private fun createRepository(name: String, sailParams: Map<String, String>): SailRepository {
            val sail = OwlimSchemaRepository().apply { setParameters(sailParams) }
            return SailRepository(sail).apply {
                dataDir = tmpFolder.newFolder("SituatedInference-${sailParams["ruleset"]}-$name"); init()
            }
        }

        @JvmStatic
        @BeforeClass
        fun setUp() {
            setWorkDir()
        }

        @JvmStatic
        fun setWorkDir() {
            System.setProperty("graphdb.home.work", "${tmpFolder.root}")
            Config.reset()
        }

        @JvmStatic
        @AfterClass
        fun cleanUp() {
            resetWorkDir()
        }

        @JvmStatic
        fun resetWorkDir() {
            System.clearProperty("graphdb.home.work")
            Config.reset()
        }
    }
}

//TODO add exception rethrow
inline fun <R> SailRepository.use(block: (SailRepository) -> R): R {
    return try {
        block(this)
    } finally {
        this.shutDown()
    }
}

/**
 * The default equality method for [com.ontotext.trree.query.evaluation.GraphDBBindingSet] takes into account implementation details that falsify the result.
 * Adding the same statements in a different order generates different ids for the same values.
 */
internal fun BindingSet.toStringValueMap(): Map<String, String> =
    associate { it.name to it.value.stringValue() }

public infix fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> = (this - other) + (other - this)