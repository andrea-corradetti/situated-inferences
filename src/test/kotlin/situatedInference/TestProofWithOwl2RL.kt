package situatedInference

import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.RepositoryException
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.junit.*
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail


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
            
                graph conj:schemas\/s {
                    <urn:family> a conj:SituatedContext.
                    rdf4j:nil a conj:SharedKnowledgeContext.
                 }
                     
                conj:task conj:situateSchema conj:schemas\/s.
            
                
                graph <urn:family> {
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
               graph conj:schemas\/s {
                    <urn:family> a conj:SituatedContext.
                    rdf4j:nil a conj:SharedKnowledgeContext.
                 }
                     
                conj:task conj:situateSchema conj:schemas\/s.
            
                graph <urn:family> {
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
                 conj:task conj:situateSchema conj:schemas\/s.
            
               graph conj:schemas\/s {
                    rdf4j:nil a conj:SituatedContext.
                 }
                
                graph rdf4j:nil {
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

        println("result1 ${result1.size} $result1")
        println("result2 ${result2.size} $result2")
        println("result3 ${result3.size} $result3")


        assert(result1 == result2) {
            println("result1 and result2 are different: ${result1 symmetricDifference result2} ")
        }



        assert(result2 == result3) {
            println("result2 and result3 are different: ${result2 symmetricDifference result3} ")
        }
    }

    @Test
    fun `Rechecking for incosistencies raises exception`() {
        val insertSchema = """
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
                  
        
            #########################################
            #                                       #
            #            NAMED GRAPHS               #
            #                                       #
            #    This should NEVER be consistent    #
            #                                       #
            #########################################
            }
        """.trimIndent()

        val insertInconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
        
            INSERT DATA {
                   GRAPH :LoisLanesThoughts {
                        :Superman owl:differentFrom :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :MarthaKentsThoughts {
                       :Superman owl:sameAs :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :myThoughts {
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
                conj:thoughts conj:situate ( <http://rdf4j.org/schema/rdf4j#nil> :LoisLanesThoughts :MarthaKentsThoughts :myThoughts )  .
                
                graph conj:thoughts {
                    ?s ?p ?o 
                }
            }
        """.trimIndent()

        val checkForInconsistencies = """
            prefix sys: <http://www.ontotext.com/owlim/system#>
            
            INSERT DATA {
                _:b sys:consistencyCheckAgainstRuleset "${sailParams["ruleset"]}"
            }
        """.trimIndent()

        createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(insertSchema).execute()
                it.prepareUpdate(insertInconsistentData).execute()
                val exception =
                    assertFailsWith(RepositoryException::class, "PluginConsistencyException not thrown") {
                        it.prepareUpdate(checkForInconsistencies).execute()
                    }
            }
        }
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
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            select distinct ?s ?p ?o where {
                #conj:defaultGraph conj:situate (<http://rdf4j.org/schema/rdf4j#nil>)  .
                
                ?task conj:situateSchema conj:schemas\/s1
                
                graph conj:schemas\/s1 {
                    rdf4j:nil a conj:SituatedContext
                }
                
                graph rdf4j:nil {
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
                ?s ?p ?o
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
                it.prepareTupleQuery(selectAllFromDefault).evaluate()
                    .mapTo(HashSet(), BindingSet::toStringValueMap)
            }
        }

        println("result1 ${result1.size} $result1")
        println("result2 ${result2.size} $result2")

        assert(result1 == result2) {
            println("result1 and result2 are different because of: ${result1 symmetricDifference result2} ")
        }

        assert(result1.isNotEmpty()) { println("Result1 is empty") }
        assert(result2.isNotEmpty()) { println("Result2 is empty") }
    }


    @Ignore
    @Test
    fun `Correct antecedents are identified in a situation`() {

    }

    @Test
    fun `Situate with schema and hardcode graph name`() {
        val addNamedGraphs = """
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

        val situateWithSchema = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o where {
            
                conj:task conj:situateSchema conj:schemas\/thoughts.
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts
                }
            
                graph :LoisLanesThoughts-situated {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()

        val result = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situateWithSchema).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }
        println("result set $result")
        assert(result.isNotEmpty()) {
            println("result is empty")
        }
    }

    @Test
    fun `Situate with schema while manually binding new graphs`() {
        val addNamedGraphs = """
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

        val situateWithSchema = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
            
              ?task conj:situateSchema conj:schemas\/thoughts.
                      
              graph conj:schemas\/thoughts {
                  rdf4j:nil a conj:SharedKnowledgeContext.
                  :LoisLanesThoughts a conj:SituatedContext.
                  :MarthaKentsThoughts a conj:SituatedContext.
                  :myThoughts a conj:SituatedContext.
                  "-situated" a conj:appendToContexts
              }
                
                bind (:LoisLanesThoughts-situated as ?g1).
            
                graph ?g1 {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()

        val result = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situateWithSchema).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        println("result set ${result.size} $result")
        assert(result.isNotEmpty())
        assert(result.size == 136)
        assert(result.all { it["g1"] == "http://a#LoisLanesThoughts-situated" })

    }

    @Test
    fun `Insert statements after situating`() {
        val addNamedGraphs = """
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

        val situateWithSchema = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            insert {
                graph ?g1 {
                    ?s ?p ?o
                }
            } where {
            
              ?task conj:situateSchema conj:schemas\/thoughts.
                      
              graph conj:schemas\/thoughts {
                  rdf4j:nil a conj:SharedKnowledgeContext.
                  :LoisLanesThoughts a conj:SituatedContext.
                  :MarthaKentsThoughts a conj:SituatedContext.
                  :myThoughts a conj:SituatedContext.
              }
                
                bind (:LoisLanesThoughts as ?g1).
            
                graph ?g1 {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()

        val queryLoisLaneThoughts = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                bind (:LoisLanesThoughts as ?g1).
                graph ?g1 {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()


        val result = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareUpdate(situateWithSchema).execute()
                it.prepareTupleQuery(queryLoisLaneThoughts).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        println("result set ${result.size} $result")
        assert(result.isNotEmpty())
        assert(result.size == 136)
        assert(result.all { it["g1"] == "http://a#LoisLanesThoughts" })

    }

    @Test
    fun `Situate with schema while binding automatically`() {
        val addNamedGraphs = """
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

        val situateWithSchema = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                            
                ?task conj:situateSchema conj:schemas\/thoughts.
           
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts
                }
                
                ?task conj:hasSituatedContext ?g1.
                
                graph ?g1 {
                    ?s ?p ?o .
                }  
            }
        """.trimIndent()

        val result = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situateWithSchema).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        println("result set ${result.size} $result")
        assert(result.size == 408)

    }

    @Test
    fun `Situate with prefix produces result`() {
        val addNamedGraphs = """
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

        val situateWithSchema = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
            
                ?task conj:situateSchema conj:schemas\/thoughts.
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts.

                }
            
                ?task conj:hasSituatedContext ?g1.
            
                graph ?g1 {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()

        val situateWithSchemaAndPrefix = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
            
                conj:task conj:situateSchema conj:schemas\/thoughts.
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    <http://a#> a conj:prefixToSituate.
                      "-situated" a conj:appendToContexts.
                }
            
                conj:task conj:hasSituatedContext ?g1.
            
                graph ?g1 {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situateWithSchema).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        val result2 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situateWithSchemaAndPrefix).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        println("result1 set ${result1.size} $result1")
        println("result2 set ${result2.size} $result2")

        assert(result1.isNotEmpty())
        assert(result2.isNotEmpty())
        assert(result1 == result2)

    }

    @Test
    fun `Situate with regex produces result`() {
        val addNamedGraphs = """
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

        val situateWithSchema = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
            
                conj:task conj:situateSchema conj:schemas\/thoughts.
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                      "-situated" a conj:appendToContexts.

                }
            
                conj:task conj:hasSituatedContext ?g1.
            
                graph ?g1 {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()

        val situateWithSchemaAndRegex = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                conj:task conj:situateSchema conj:schemas\/thoughts.
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    "(?i).*thoughts.*" a conj:regexToSituate.
                    "-situated" a conj:appendToContexts.

                }
            
                conj:task conj:hasSituatedContext ?g1.
            
                graph ?g1 {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situateWithSchema).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        val result2 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situateWithSchemaAndRegex).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        println("result1 set ${result1.size} $result1")
        println("result2 set ${result2.size} $result2")

        assert(result1.isNotEmpty())
        assert(result2.isNotEmpty())
        assert(result1 == result2)
    }

    @Test
    fun `situate 2`() {
        val addNamedGraphs = """
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

        val situate2 = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts.
                }
                
                ?task conj:hasSituatedContext ?g1.
                
                ?task conj:situateSchema conj:schemas\/thoughts.
                
                graph ?g1 {
                    ?s ?p ?o .
                }  
            }
        """.trimIndent()

        val result2 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situate2).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        println("result2 ${result2.size} $result2")
        assert(result2.size == 408)

    }

    @Test
    fun `situate 3`() {
        val addNamedGraphs = """
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

        val situate3 = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                            
               graph ?g1 {
                   ?s ?p ?o .
               }  
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts.
                }
                
                ?task conj:hasSituatedContext ?g1.
                
                ?task conj:situateSchema conj:schemas\/thoughts.
           }
        """.trimIndent()

        val result3 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situate3).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }

        println("result3 ${result3.size} $result3")
        assert(result3.size == 408)

    }

    @Test
    fun `Reordering statements produces same result `() {
        val addNamedGraphs = """
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

        val situate1 = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                            
                ?task conj:situateSchema conj:schemas\/thoughts.
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts
                }
                
                ?task conj:hasSituatedContext ?g1.
                
                graph ?g1 {
                    ?s ?p ?o .
                }  
            }
        """.trimIndent()

        val situate2 = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts.
                }
                
                ?task conj:hasSituatedContext ?g1.
                
                ?task conj:situateSchema conj:schemas\/thoughts.
                
                graph ?g1 {
                    ?s ?p ?o .
                }  
            }
        """.trimIndent()
        val situate3 = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                            
               graph ?g1 {
                   ?s ?p ?o .
               }  
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts
                }
                
                ?task conj:hasSituatedContext ?g1.             
                ?task conj:situateSchema conj:schemas\/thoughts.            
            }
        """.trimIndent()

        val (result1, result2, result3) = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                arrayOf(
                    it.prepareTupleQuery(situate1).evaluate().map(BindingSet::toStringValueMap).toSet(),
                    it.prepareTupleQuery(situate2).evaluate().map(BindingSet::toStringValueMap).toSet(),
                    it.prepareTupleQuery(situate3).evaluate().map(BindingSet::toStringValueMap).toSet(),
                )
            }
        }
        println("result1 ${result1.size} $result1")
        println("result2 ${result2.size} $result2")
        println("result3 ${result3.size} $result3")

        assert(result1.size == 408)
        assert(result2.size == 408)
        assert(result3.size == 408)

        assert(result1 == result2)
        assert(result2 == result3)

    }

    @Test
    fun `Reordering statements is not empty `() {
        val addNamedGraphs = """
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

        val situate1 = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                ?task conj:hasSituatedContext ?g1.
                ?task conj:situateSchema conj:schemas\/thoughts.
                
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                    "-situated" a conj:appendToContexts
                }

               graph ?g1 {
                   ?s ?p ?o .
               }  
            }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                it.prepareTupleQuery(situate1).evaluate().map(BindingSet::toStringValueMap).toSet()

            }
        }
        println("result1 ${result1.size} $result1")

        assert(result1.isNotEmpty())

    }

    @Ignore
    @Test
    fun `Renaming situations`() {
        val addNamedGraphs = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
        
            INSERT DATA {
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

        val situate1 = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {       
                ?task conj:hasSituatedContext ?g1.

                ?task conj:situateSchema conj:schemas\/thoughts.
                ?task conj:appendToContexts "-situated".
                
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                }

               graph ?g1 {
                   ?s ?p ?o .
               }  
            }
        """.trimIndent()

//        val result1 = createCleanRepositoryWithDefaults().use { repo ->
//            repo.connection.use {
//                it.prepareUpdate(addNamedGraphs).execute()
//                it.prepareTupleQuery(situate1).evaluate().map(BindingSet::toStringValueMap).toSet()
//
//            }
//        }
//        println("result1 ${result1.size} $result1")
//
//        assert(result1.isNotEmpty())
        fail()
    }


    @Test
    fun `Reified statement is represented correctly as singleton`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           REIFICATION               #
                #                                     #
                #######################################
    
                :S rdf:type rdf:Statement .
                :S rdf:subject :Superman .
                :S rdf:predicate :can .
                :S rdf:object :clingFromCeiling .
                
                :I :thinks :S.
                :S :since "2023".
                
            }  
        """.trimIndent()

        val convertReifiedStatementWithEmbeddedTriple = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select ?subject ?predicate ?object where {
                    
                    :S conj:asSingleton conj:singleton
                    
                    graph conj:singleton {
                        ?subject ?predicate ?object
                    }
                    
                    ##:reified-situated conj:situate :reified
                    
                    #graph :reified-situated {
                    #    ?s ?p ?o. 
                    #}
                }
        """.trimIndent()

        val convertReifiedStatement = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select ?subject ?predicate ?object where {
                    
                    :S conj:asSingleton conj:singleton
                    
                    graph conj:singleton {
                        ?subject ?predicate ?object
                    }
                    
                    ##:reified-situated conj:situate :reified
                    
                    #graph conj:singleton {
                    #    ?s ?p ?o. 
                    #}
                }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(convertReifiedStatement).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(result1.isNotEmpty())
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#Superman",
                    "predicate" to "http://a#can",
                    "object" to "http://a#clingFromCeiling",
                )
            )
        )
    }

    @Test
    fun `Reified statement is situated correctly`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           REIFICATION               #
                #                                     #
                #######################################
    
                :S rdf:type rdf:Statement .
                :S rdf:subject :Superman .
                :S rdf:predicate :can .
                :S rdf:object :clingFromCeiling .
                
                :I :thinks :S.
                :S :since "2023".
                
            }  
        """.trimIndent()

        val convertReifiedStatement = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select ?subject ?predicate ?object where {
                    
                    :S conj:asSingleton conj:singleton.
                    
                    ?situation conj:situate (conj:singleton rdf4j:nil)
                    
                    graph ?situation {
                        ?subject ?predicate ?object.
                    }
                }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(convertReifiedStatement).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(result1.isNotEmpty())
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#Superman",
                    "predicate" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                    "object" to "http://t#SpiderSuperHero"
                )
            )
        )
    }

    @Test
    fun `Situated singleton is reified correctly`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           REIFICATION               #
                #                                     #
                #######################################
    
                :S rdf:type rdf:Statement .
                :S rdf:subject :Superman .
                :S rdf:predicate :can .
                :S rdf:object :clingFromCeiling .
                
                :I :thinks :S.
                :S :since "2023".
                
            }  
        """.trimIndent()

        val convertReifiedStatement = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct ?subject ?predicate ?object where {
                    :S conj:asSingleton conj:singleton.
                    ?situation conj:situate (conj:singleton rdf4j:nil).
                    
                    ?reifiedGraph conj:reifiesGraph ?situation
                    
                    graph ?reifiedGraph {
                        ?subject ?predicate ?object.
                    } 
                }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(convertReifiedStatement).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(result1.isNotEmpty())

        assert(
            result1.groupBy { it["subject"]!! }.any { (statement, reification) ->
                reification.containsAll(
                    listOf(
                        mapOf(
                            "subject" to statement,
                            "predicate" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                            "object" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement"
                        ),
                        mapOf(
                            "subject" to statement,
                            "predicate" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#subject",
                            "object" to "http://a#Superman"
                        ),
                        mapOf(
                            "subject" to statement,
                            "predicate" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate",
                            "object" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                        ),
                        mapOf(
                            "subject" to statement,
                            "predicate" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#object",
                            "object" to "http://t#SpiderSuperHero"
                        ),
                    )
                )
            }
        )
    }

    @Test
    fun `Reified statement converted to triple`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           REIFICATION               #
                #                                     #
                #######################################
    
                :S rdf:type rdf:Statement .
                :S rdf:subject :Superman .
                :S rdf:predicate :can .
                :S rdf:object :clingFromCeiling .
                
                :I :thinks :S.
                :S :since "2023".
                
            }  
        """.trimIndent()

        val convertReifiedStatement = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select ?subject ?predicate ?object where {
                    :S conj:asTriple ?triple.
                    
                    :S conj:asTriple ?triple .
                    bind(rdf:subject(?triple) as ?subject)
                    bind(rdf:predicate(?triple) as ?predicate)
                    bind(rdf:object(?triple) as ?object)
                }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(convertReifiedStatement).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(result1.size == 1)
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#Superman",
                    "predicate" to "http://a#can",
                    "object" to "http://a#clingFromCeiling"
                )
            )
        )
    }

    @Test
    fun `Graph reified correctly`() {
        val addGraphToReify = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
            
            INSERT DATA {
                graph :g1 {
                    :Superman a t:Person.
                }
            }  
        """.trimIndent()

        val reifyGraph = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
            
            
            select ?subject ?predicate ?object where {
               
                ?reifiedGraph conj:reifiesGraph :g1
                
                graph ?reifiedGraph {
                    ?subject ?predicate ?object.
                }
            }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addGraphToReify).execute()
                it.prepareTupleQuery(reifyGraph).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(result1.isNotEmpty())
        assert(result1.size == 4)
        assert(
            result1.any {
                it["predicate"] == "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                        && it["object"] == "http://www.w3.org/1999/02/22-rdf-syntax-ns#Statement"
            }
        )
        assert(
            result1.any {
                it["predicate"] == "http://www.w3.org/1999/02/22-rdf-syntax-ns#subject"
                        && it["object"] == "http://a#Superman"
            }
        )
        assert(
            result1.any {
                it["predicate"] == "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate"
                        && it["object"] == "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
            }
        )
        assert(
            result1.any {
                it["predicate"] == "http://www.w3.org/1999/02/22-rdf-syntax-ns#object"
                        && it["object"] == "http://t#Person"
            }
        )

    }

    @Test
    fun `Can bind variables in reified graphs`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           REIFICATION               #
                #                                     #
                #######################################
    
                :S rdf:type rdf:Statement .
                :S rdf:subject :Superman .
                :S rdf:predicate :can .
                :S rdf:object :clingFromCeiling .
                
                :I :thinks :S.
                :S :since "2023".
                
            }  
        """.trimIndent()

        val convertReifiedStatement = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct ?subject ?predicate ?object where {
                    :S conj:asSingleton conj:singleton.
                    ?situation conj:situate (conj:singleton rdf4j:nil).
                    
                    ?reifiedGraph conj:reifiesGraph ?situation
                    
                    bind (:S as ?subject)
                    graph ?reifiedGraph {
                        ?subject ?predicate ?object.
                    } 
                }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(convertReifiedStatement).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }
        println("result1 set ${result1.size} $result1")
        assert(result1.size == 4) //TODO write better checks
    }


    @Test
    fun `Quoting a reified graph produces quoting of reified statements`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           REIFICATION               #
                #                                     #
                #######################################
    
                :S rdf:type rdf:Statement .
                :S rdf:subject :Superman .
                :S rdf:predicate :can .
                :S rdf:object :clingFromCeiling .
                
                :I :thinks :S.
                :S :since "2023".
                
            }  
        """.trimIndent()

        val convertReifiedStatementWithSchema = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct ?rs ?predicate ?object where {
                    :S conj:asSingleton conj:singleton.
                    
                    ?task conj:situateSchema conj:schemas\/s1.
                    
                    graph conj:schemas\/s1 {
                        conj:singleton a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                    }
                    
                    ?task conj:hasSituatedContext ?g.
                    
                    ?reifiedGraph conj:reifiesGraph ?g.
                    
                    graph ?reifiedGraph {
                        ?rs ?rp ?ro.
                    } 
                    
                    ?rs ?predicate ?object.
                    
                }
        """.trimIndent()

        val convertReifiedStatement = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct ?rs ?predicate ?object where {
                
                    :S conj:asSingleton conj:singleton.
                    ?g conj:situate (conj:singleton rdf4j:nil).
                    
                    #?task conj:hasSituatedContext ?g.
                    
                    ?reifiedGraph conj:reifiesGraph ?g.
                    
                    graph ?reifiedGraph {
                        ?rs ?rp ?ro.
                    } 
                    
                    ?rs ?predicate ?object
                }
        """.trimIndent()

        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(convertReifiedStatementWithSchema).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }
        println("result1 set ${result1.size} $result1")
        assertTrue {
            val regex = Regex("(-?\\d+)-(-?\\d+)-(-?\\d+)|http://a#S")

            result1.isNotEmpty() && result1.all { regex.containsMatchIn(it["rs"]!!) }
        }
    }

    @Test
    fun `Singleton is quoted correctly after converting reification`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           REIFICATION               #
                #                                     #
                #######################################
    
                :S rdf:type rdf:Statement .
                :S rdf:subject :Superman .
                :S rdf:predicate :can .
                :S rdf:object :clingFromCeiling .
                
                :I :thinks :S.
                :S :since "2023".
                
            }  
        """.trimIndent()

        val getStatementsQuotingReification = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct ?subject ?predicate ?object where {
                    :S conj:asSingleton conj:singleton.
                    
                    bind (:S as ?subject)
                    ?subject ?predicate ?object
                }
        """.trimIndent()

        val getStatementQuotingSingleton = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct ?subject ?predicate ?object where {
                    :S conj:asSingleton ?singleton.
                    
                    bind (?singleton as ?subject)
                    ?subject ?predicate ?object
                }
        """.trimIndent()

        val (result1, result2) = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                listOf(
                    it.prepareTupleQuery(getStatementsQuotingReification).evaluate().map(BindingSet::toStringValueMap)
                        .toSet(),
                    it.prepareTupleQuery(getStatementQuotingSingleton).evaluate().map(BindingSet::toStringValueMap)
                        .toSet(),
                )
            }
        }
        println("result1 set ${result1.size} $result1")
        println("result2 set ${result2.size} $result2")
        assert(result1.isNotEmpty())
        assert(result2.isNotEmpty())

        val grouped = (result1 + result2).groupBy { it["predicate"] to it["object"] }.mapValues { (pair, list) ->
            list.map { it["subject"] }
        }
        println(grouped)
        assert(result1.size == result2.size) //TODO write better checks
    }

    @Test
    fun `Singleton is quoted correctly after inference`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           REIFICATION               #
                #                                     #
                #######################################
    
                :S rdf:type rdf:Statement .
                :S rdf:subject :Superman .
                :S rdf:predicate :can .
                :S rdf:object :clingFromCeiling .
                
                :I :thinks :S.
                :S :since "2023".
                
            }  
        """.trimIndent()

        val getStatementsQuotingReification = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct ?subject ?predicate ?object where {
                    :S conj:asSingleton conj:singleton.
                    
                    bind (:S as ?subject)
                    ?subject ?predicate ?object
                }
        """.trimIndent()

        val getStatementQuotingSingleton = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct ?subject ?predicate ?object where {
                    :S conj:asSingleton conj:singleton.
                    
                    graph conj:schemas\/s1 {
                        rdf4j:nil a conj:SharedKnowledgeContext.
                        conj:singleton a conj:SituatedContext.
                    }
                    
                    ?situation conj:situateSchema conj:schemas\/s1.
                    
                    ?situation conj:hasSituatedContext ?g
                    
                    bind (?g as ?subject).
                    ?subject ?predicate ?object.
                }
        """.trimIndent()

        val (result1, result2) = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                listOf(
                    it.prepareTupleQuery(getStatementsQuotingReification).evaluate().map(BindingSet::toStringValueMap)
                        .toSet(),
                    it.prepareTupleQuery(getStatementQuotingSingleton).evaluate().map(BindingSet::toStringValueMap)
                        .toSet(),
                )
            }
        }
        println("result1 set ${result1.size} $result1")
        println("result2 set ${result2.size} $result2")
        assert(result1.isNotEmpty())
        assert(result2.isNotEmpty())

        val grouped = (result1 + result2).groupBy { it["predicate"] to it["object"] }.mapValues { (pair, list) ->
            list.map { it["subject"] }
        }
        println(grouped)
        assert(result1.size == result2.size) //TODO write better checks
    }

    @Test
    fun `Triple group from rdfstar is created correctly`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           RDF-STAR                  #
                #                                     #
                #######################################
    
            :I :thinks << :Superman :can :clingFromCeiling >>.
                
            }  
        """.trimIndent()

        val getStatementsQuotingEmbedded = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                
                PREFIX : <http://a#>
                
                select distinct * where {
                    {
                        select distinct ?subject ?predicate ?object ?g where {
                            :I :thinks ?triple.
                            
                            bind (conj:situations\/I-thinks as ?g)
                            
                            ?g conj:graphFromEmbedded ?triple.
                            
                            graph ?g {
                                ?subject ?predicate ?object.
                            }
                        } 
                    } 
                    UNION
                    {
                        select distinct ?subject ?predicate ?object where {
                            :I :thinks ?triple.
                            bind (?triple as ?object).
                            ?subject ?predicate ?object.
                        }
                    }
                }
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(getStatementsQuotingEmbedded).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(
            result1.any {
                it.entries.containsAll(
                    mapOf(
                        "subject" to "http://a#Superman",
                        "predicate" to "http://a#can",
                        "object" to "http://a#clingFromCeiling",
                        "g" to "https://w3id.org/conjectures/situations/I-thinks"
                    ).entries
                )
            }
        )

        assert(
            result1.any {
                it.entries.containsAll(
                    mapOf(
                        "subject" to "http://a#I",
                        "predicate" to "http://a#thinks",
                        "object" to "https://w3id.org/conjectures/situations/I-thinks"
                    ).entries
                )
            }
        )
    }

    @Test
    fun `Rdfstar graph is situated correctly with schema`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           RDF-STAR                  #
                #                                     #
                #######################################
    
            :I :thinks << :Superman :can :clingFromCeiling >>.
                
            }  
        """.trimIndent()

        val getStatementsQuotingEmbedded = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                select distinct  ?subject ?predicate ?object where {
                    :I :thinks ?triple.
                    
                    bind (conj:situations\/I-thinks as ?g)
             
                    ?g conj:groupsTriple ?triple.
                     
                     graph conj:schemas\/s {
                        ?g a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                     }
                     
                    conj:task conj:situateSchema conj:schemas\/s.
                    
                    #:I :thinks ?object.                    
                    graph ?g {
                        ?subject ?predicate ?object
                    }
                }
               
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(getStatementsQuotingEmbedded).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(result1.isNotEmpty())
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#Superman",
                    "predicate" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                    "object" to "http://t#SpiderSuperHero"
                )
            )
        )
    }

    @Test
    fun `Situating statements for rdfstar with schema are returned correctly`() {
        val addReifiedStatement = """
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
                    
                #######################################
                #                                     #
                #           RDF-STAR                  #
                #                                     #
                #######################################
    
            :I :thinks << :Superman :can :clingFromCeiling >>.
                
            }  
        """.trimIndent()

        val getStatementsQuotingEmbedded = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                select distinct  ?subject ?predicate ?object ?context where {
                    :I :thinks ?triple.
                    
                    bind (conj:situations\/I-thinks as ?g)
             
                    ?g conj:groupsTriple ?triple.
                     
                     graph conj:schemas\/s {
                        ?g a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                     }
                     
                    conj:task conj:situateSchema conj:schemas\/s.
                            
                    {
                        bind (?g as ?object).
                        graph ?context {
                            ?subject ?predicate ?object . 
                        }
                    } 
                    UNION
                    {
                        bind (?g as ?subject).
                        graph ?context {
                            ?subject ?predicate ?object . 
                        }
                    }
                    UNION
                    {
                        graph ?context {
                            ?subject ?predicate ?object
                        }
                    }
                    
                }
               
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(getStatementsQuotingEmbedded).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(result1.isNotEmpty())
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#Superman",
                    "predicate" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                    "object" to "http://t#SpiderSuperHero",
                    "context" to "https://w3id.org/conjectures/situations/I-thinks"
                )
            )
        )
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#I",
                    "predicate" to "http://a#thinks",
                    "object" to "https://w3id.org/conjectures/situations/I-thinks",
                    "context" to "http://www.ontotext.com/explicit"
                )

            )
        )
    }

    @Test
    fun `Graph for rdf-star is expanded in situating triples`() {
        val addReifiedStatement = """
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
                :Flash a t:Person.
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
                    
                #######################################
                #                                     #
                #           RDF-STAR                  #
                #                                     #
                #######################################
    
            :I :thinks << :Flash :can :clingFromCeiling >>.
   
            :I :thinks << :Superman :can :clingFromCeiling >>.
                
            }  
        """.trimIndent()

        val getStatementsQuotingEmbedded = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                select distinct  ?subject ?predicate ?object ?context where {
                    :I :thinks ?triple.
                    
                    bind (conj:situations\/I-thinks as ?g)
             
                    ?g conj:groupsTriple ?triple.
                     
                     graph conj:schemas\/s {
                        ?g a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                     }
                     
                    conj:task conj:situateSchema conj:schemas\/s.
                    
                    conj:expanded conj:expands ?g
                            
                    {
                        bind (conj:expanded as ?object).
                        graph ?context {
                            ?subject ?predicate ?object . 
                        }
                    } 
                    UNION
                    {
                        bind (conj:expanded as ?subject).
                        graph ?context {
                            ?subject ?predicate ?object . 
                        }
                    }
                    UNION
                    {
                        graph ?context {
                            ?subject ?predicate ?object
                        }
                    }
                    
                }
               
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addReifiedStatement).execute()
                it.prepareTupleQuery(getStatementsQuotingEmbedded).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }
        println("result1 set ${result1.size} $result1")

        assert(result1.isNotEmpty())
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#Superman",
                    "predicate" to "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                    "object" to "http://t#SpiderSuperHero",
                    "context" to "https://w3id.org/conjectures/situations/I-thinks"
                )
            )
        )
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#I",
                    "predicate" to "http://a#thinks",
                    "object" to "https://w3id.org/conjectures/situations/I-thinks",
                    "context" to "http://www.ontotext.com/explicit"
                )
            )
        )
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#I",
                    "predicate" to "http://a#thinks",
                    "object" to "<<http://a#Superman http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://t#SpiderSuperHero>>",
                    "context" to "http://www.ontotext.com/explicit"
                )
            )
        )
        assert(
            result1.contains(
                mapOf(
                    "subject" to "http://a#I",
                    "predicate" to "http://a#thinks",
                    "object" to "<<http://a#Flash http://www.w3.org/1999/02/22-rdf-syntax-ns#type http://t#SpiderSuperHero>>",
                    "context" to "http://www.ontotext.com/explicit"
                )
            )
        )
    }

    @Test
    fun `Inconsistency between 2 situated contexts identified`() {
        val insertSharedKnowledge = """
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
                  
        

            }
        """.trimIndent()

        val insertInconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
            
            #########################################
            #                                       #
            #            NAMED GRAPHS               #
            #                                       #
            #    This should NEVER be consistent    #
            #                                       #
            #########################################
        
            INSERT DATA {
                   GRAPH :LoisLanesThoughts {
                        :Superman owl:differentFrom :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :MarthaKentsThoughts {
                       :Superman owl:sameAs :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :myThoughts {
                        :Superman owl:sameAs :ClarkKent.
                        :Superman a t:FictionalPerson .
                    }
            }
        """.trimIndent()

        val SelectInconsistentWith = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                select distinct ?s ?p ?o where {
                    
                     graph conj:schemas\/s {
                        :MarthaKentsThoughts a conj:SituatedContext.
                        :myThoughts a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                     }
                     
                    conj:task conj:situateSchema conj:schemas\/s.
                
                    VALUES (?s ?p ?o) { (:MarthaKentsThoughts conj:disagreesWith :myThoughts) }
                    ?s ?p ?o
                }
               
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(insertSharedKnowledge).execute()
                it.prepareUpdate(insertInconsistentData).execute()
                it.prepareTupleQuery(SelectInconsistentWith).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }

        println("result1 set ${result1.size} $result1")
        assert(result1.size == 1)
    }

    @Test
    fun `Asking for inconsistency check returns true`() {
        val insertSharedKnowledge = """
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
                  
        

            }
        """.trimIndent()

        val insertInconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
            
            #########################################
            #                                       #
            #            NAMED GRAPHS               #
            #                                       #
            #    This should NEVER be consistent    #
            #                                       #
            #########################################
        
            INSERT DATA {
                   GRAPH :LoisLanesThoughts {
                        :Superman owl:differentFrom :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :MarthaKentsThoughts {
                       :Superman owl:sameAs :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :myThoughts {
                        :Superman owl:sameAs :ClarkKent.
                        :Superman a t:FictionalPerson .
                    }
            }
        """.trimIndent()

        val SelectInconsistentWith = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                ASK {
                     graph conj:schemas\/s {
                        :MarthaKentsThoughts a conj:SituatedContext.
                        :myThoughts a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                     }
                     
                    conj:task conj:situateSchema conj:schemas\/s.
                
                    VALUES (?s ?p ?o) { (:MarthaKentsThoughts conj:disagreesWith :myThoughts) }
                    ?s ?p ?o
                }
               
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(insertSharedKnowledge).execute()
                it.prepareUpdate(insertInconsistentData).execute()
                it.prepareBooleanQuery(SelectInconsistentWith).evaluate()
            }
        }

        println("result1 $result1")
        assert(result1)
    }

    @Test
    fun `Find disagreemnt with unbound object`() {
        val insertSharedKnowledge = """
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
                  
        

            }
        """.trimIndent()

        val insertInconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
            
            #########################################
            #                                       #
            #            NAMED GRAPHS               #
            #                                       #
            #    This should NEVER be consistent    #
            #                                       #
            #########################################
        
            INSERT DATA {
                   GRAPH :LoisLanesThoughts {
                        :Superman owl:differentFrom :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :MarthaKentsThoughts {
                       :Superman owl:sameAs :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :myThoughts {
                        :Superman owl:sameAs :ClarkKent.
                        :Superman a t:FictionalPerson .
                    }
            }
        """.trimIndent()

        val SelectInconsistentWith = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                select distinct ?s ?p ?o where {
                    
                     graph conj:schemas\/s {
                        :MarthaKentsThoughts a conj:SituatedContext.
                        :myThoughts a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                     }
                     
                    conj:task conj:situateSchema conj:schemas\/s.
                
                    VALUES (?s ?p) { (:MarthaKentsThoughts conj:disagreesWith) }
                    ?s ?p ?o
                }
               
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(insertSharedKnowledge).execute()
                it.prepareUpdate(insertInconsistentData).execute()
                it.prepareTupleQuery(SelectInconsistentWith).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }

        println("result1 ${result1.size} $result1")
        assert(result1.isNotEmpty())
    }

    @Test
    fun `Find disagreemnt with unbound subject`() {
        val insertSharedKnowledge = """
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
                  
        

            }
        """.trimIndent()

        val insertInconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
            
            #########################################
            #                                       #
            #            NAMED GRAPHS               #
            #                                       #
            #    This should NEVER be consistent    #
            #                                       #
            #########################################
        
            INSERT DATA {
                   GRAPH :LoisLanesThoughts {
                        :Superman owl:differentFrom :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :MarthaKentsThoughts {
                       :Superman owl:sameAs :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :myThoughts {
                        :Superman owl:sameAs :ClarkKent.
                        :Superman a t:FictionalPerson .
                    }
            }
        """.trimIndent()

        val SelectInconsistentWith = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                select distinct ?s ?p ?o where {
                    
                     graph conj:schemas\/s {
                        :MarthaKentsThoughts a conj:SituatedContext.
                        :myThoughts a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                     }
                     
                    conj:task conj:situateSchema conj:schemas\/s.
                
                    VALUES (?p ?o) { (conj:disagreesWith :MarthaKentsThoughts) }
                    ?s ?p ?o
                }
               
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(insertSharedKnowledge).execute()
                it.prepareUpdate(insertInconsistentData).execute()
                it.prepareTupleQuery(SelectInconsistentWith).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }

        println("result1 ${result1.size} $result1")
        assert(result1.isNotEmpty())
    }

        @Test
    fun `Find all contexts that disagree`() {
        val insertSharedKnowledge = """
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
                  
        

            }
        """.trimIndent()

        val insertInconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
            
            #########################################
            #                                       #
            #            NAMED GRAPHS               #
            #                                       #
            #    This should NEVER be consistent    #
            #                                       #
            #########################################
        
            INSERT DATA {
                   GRAPH :LoisLanesThoughts {
                        :Superman owl:differentFrom :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :MarthaKentsThoughts {
                       :Superman owl:sameAs :ClarkKent.
                        :Superman a t:RealPerson .
                    }

                   GRAPH :myThoughts {
                        :Superman owl:sameAs :ClarkKent.
                        :Superman a t:FictionalPerson .
                    }
            }
        """.trimIndent()

        val SelectInconsistentWith = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                select distinct ?s ?p ?o where {
                    
                     graph conj:schemas\/s {
                        :MarthaKentsThoughts a conj:SituatedContext.
                        :myThoughts a conj:SituatedContext.
                        rdf4j:nil a conj:SharedKnowledgeContext.
                     }
                     
                    conj:task conj:situateSchema conj:schemas\/s.
                
                    VALUES (?p) { (conj:disagreesWith) }
                    ?s ?p ?o
                }
               
        """.trimIndent()


        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(insertSharedKnowledge).execute()
                it.prepareUpdate(insertInconsistentData).execute()
                it.prepareTupleQuery(SelectInconsistentWith).evaluate().map(BindingSet::toStringValueMap)
                    .toSet()
            }
        }

        println("result1 ${result1.size} $result1")
        assert(result1.isNotEmpty())
    }

      @Test
    fun `If a context is inconsistent, it disagrees with itself`() {
          val insertInconsistentData = """
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX : <http://a#>
            PREFIX t: <http://t#>
            
            INSERT DATA {

                   GRAPH :InconsistentThoughts {
                        :Superman owl:differentFrom :ClarkKent.
                        :Superman a t:RealPerson .
                        :Superman owl:sameAs :ClarkKent.
                        :Superman a t:FictionalPerson .
                    }
            }
        """.trimIndent()

          val checkInconsistentGraph = """
                PREFIX conj: <https://w3id.org/conjectures/>
                PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX : <http://a#>      
                
                ASK {
                    :InconsistentThoughts conj:disagreesWith :InconsistentThoughts.
                }
               
        """.trimIndent()



        val result1 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(insertInconsistentData).execute()
                it.prepareBooleanQuery(checkInconsistentGraph).evaluate()
            }
        }

        println("result1 $ $result1")
        assert(result1)
    }

    companion object {
        @JvmField
        @ClassRule
        val tmpFolder = TemporaryLocalFolder()

        private val sailParams = mapOf(
            "register-plugins" to SituatedInferencePlugin::class.qualifiedName as String,
            "ruleset" to "owl2-rl",
//                "ruleset" to "owl-horst",
//            "ruleset" to "owl2-ql",
//            "check-for-inconsistencies" to "true",
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
        private fun setWorkDir() {
            System.setProperty("graphdb.home.work", "${tmpFolder.root}")
            Config.reset()
        }

        @JvmStatic
        @AfterClass
        fun cleanUp() {
            resetWorkDir()
        }

        @JvmStatic
        private fun resetWorkDir() {
            System.clearProperty("graphdb.home.work")
            Config.reset()
        }
    }
}

/**
 * The default equality method for [com.ontotext.trree.query.evaluation.GraphDBBindingSet] takes into account implementation details that falsify the result.
 * Adding the same statements in a different order generates different ids for the same values.
 */
internal fun BindingSet.toStringValueMap(): Map<String, String> =
    associate { it.name to it.value.stringValue() }

infix fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> = (this - other) + (other - this)