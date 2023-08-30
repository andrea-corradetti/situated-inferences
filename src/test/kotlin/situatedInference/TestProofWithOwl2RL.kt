package situatedInference

import com.ontotext.graphdb.Config
import com.ontotext.test.TemporaryLocalFolder
import com.ontotext.trree.OwlimSchemaRepository
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.junit.*
import java.util.*
import kotlin.test.assertFailsWith
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

    @Ignore
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
                conj:thoughts conj:situate ( <http://rdf4j.org/schema/rdf4j#nil> :LoisLanesThoughts :MarthaKentsThoughts :myThoughts )  .
                
                graph conj:thoughts {
                    ?s ?p ?o 
                }
            }
        """.trimIndent()

        createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(inconsistentData).execute()
                val exception =
                    assertFailsWith(PluginConsistencyException::class, "PluginConsistencyException not thrown") {
                        it.prepareTupleQuery(situateInconsistentData).evaluate().map(BindingSet::toStringValueMap)
                            .toSet()
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

    //    @Ignore("Must resolve infinite loop. Probably should rethink this")
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
                    .map(BindingSet::toStringValueMap).toSet()
            }
        }

        val result2 = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addToDefaultGraph).execute()
                it.prepareTupleQuery(selectAllFromDefault).evaluate().map(BindingSet::toStringValueMap).toSet()
            }
        }
        println("List 1 ${result1.size} $result1")
        println("List 2 ${result2.size} $result2")

        assert(result1 == result2) {
            println("result1 and result2 are different because of: ${result1 symmetricDifference result2} ")
        }

        assert(result1.isNotEmpty())
        assert(result2.isNotEmpty())
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
                conj:task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
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
            
                conj:task conj:situateSchema conj:schemas\/thoughts.
                conj:task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                }
            
                #conj:task conj:hasSituatedGraph ?sg.
                
                VALUES ?g1 { :LoisLanesThoughts-situated }
            
                # conj:spec conj:situatedWithSuffix "-situated".
            
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

        println("result set $result")
        assert(result.isNotEmpty())

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
           
                ?task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
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
        assert(result.isNotEmpty())

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
            
                conj:task conj:situateSchema conj:schemas\/thoughts.
                conj:task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                }
            
                conj:task conj:hasSituatedContext ?g1.
            
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
                conj:task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    <http://a#> a conj:prefixToSituate
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
                conj:task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
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
                conj:task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    "(?i).*thoughts.*" a conj:regexToSituate
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

        val situate3 = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o ?g1 where {
                            
                ?task conj:situateSchema conj:schemas\/thoughts.
           
                ?task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
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
                }
                
                ?task conj:appendToContexts "-situated".
                
                
                ?task conj:hasSituatedContext ?g1.
                
                ?task conj:situateSchema conj:schemas\/thoughts.
                
                graph ?g1 {
                    ?s ?p ?o .
                }  
            }
        """.trimIndent()
        val situate1 = """
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
                }
                
                ?task conj:appendToContexts "-situated".
                
                
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

        assert(result1.isNotEmpty())
        assert(result2.isNotEmpty())
        assert(result3.isNotEmpty())

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

    @Ignore
    @Test
    fun `Statements are materialized`() {
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

        val querySituated = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select ?s ?p ?o where {
            
                conj:task conj:situateSchema conj:schemas\/thoughts.
                conj:task conj:appendToContexts "-situated".
            
                graph conj:schemas\/thoughts {
                    rdf4j:nil a conj:SharedKnowledgeContext.
                    :LoisLanesThoughts a conj:SituatedContext.
                    :MarthaKentsThoughts a conj:SituatedContext.
                    :myThoughts a conj:SituatedContext.
                }
            
                #conj:task conj:hasSituatedGraph ?sg.
                
                VALUES ?g1 { :LoisLanesThoughts-situated }
            
                # conj:spec conj:situatedWithSuffix "-situated".
            
                graph ?g1 {
                    ?s ?p ?o .
                }           
            }
        """.trimIndent()
        val queryMaterialized = """
            PREFIX conj: <https://w3id.org/conjectures/>
            PREFIX rdf4j: <http://rdf4j.org/schema/rdf4j#>
            PREFIX : <http://a#>
            
            select * where {
                    ?s ?p ?o .     
            }
        """.trimIndent()

        val (result1, result2) = createCleanRepositoryWithDefaults().use { repo ->
            repo.connection.use {
                it.prepareUpdate(addNamedGraphs).execute()
                arrayOf(
                    it.prepareTupleQuery(querySituated).evaluate().map(BindingSet::toStringValueMap).toSet(),
                    it.prepareTupleQuery(queryMaterialized).evaluate().map(BindingSet::toStringValueMap).toSet()
                )
            }
        }
        println("result1 set ${result1.size} $result1")
        println("result2 set ${result2.size} $result2")


        assert(result2.containsAll(result1)) {
            println("result 2 does not contain ${(result1 - result2).size}  ${result1 - result2}")
        }

        assert(result1.isNotEmpty())
        assert(result2.isNotEmpty())
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
    fun `Situated singleton is reified correctly and is filtered correctly`() {
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

        TODO()
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

        assert(result1.isNotEmpty())
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
    fun `Situating statements are replicated after inferring`() {
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
                   
                    :S ?p ?o
                    
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

infix fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> = (this - other) + (other - this)