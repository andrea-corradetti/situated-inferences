package situatedInference

internal val deleteAll = """
        DELETE {?s ?p ?o} where {
                ?s ?p ?o .
        }
    """.trimIndent()

internal val deleteLessie = """
    PREFIX : <http://www.example.com/>
    
    DELETE DATA { :Lassie rdf:type :Dog. }
""".trimIndent()

internal val insertSituate = """
    PREFIX : <http://www.example.com/>
    PREFIX conj: <http://www.example.com/conj/>
    
    INSERT DATA {
        :Dog rdfs:subClassOf :Mammal.
        GRAPH :G1 {
            :Lassie a :Dog.
        }
    }
""".trimIndent()

internal val addLessie = """
         PREFIX : <http://www.example.com/>
        
         INSERT DATA {
            :Lassie rdf:type :Dog.
            :Dog rdfs:subClassOf :Mammal.
        }
    """.trimIndent()

internal val selectLassieIsDog = """
        PREFIX : <http://www.example.com/>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        Select ?subject ?predicate ?object WHERE {
          ?subject ?predicate ?object .
          FILTER (?subject = :Lassie && ?predicate = rdf:type  && ?object = :Dog)
        }
    """.trimIndent()

internal val explainLessie = """
        PREFIX : <http://www.example.com/>
        PREFIX t: <http://www.example.com/tbox/>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        prefix proof: <http://www.ontotext.com/proof/>

        select ?rule ?s ?p ?o ?context where {
            values (?subject ?predicate ?object) {(:Lassie rdf:type :Mammal)}
            ?ctx proof:explain (?subject ?predicate ?object) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

internal val insertMaryNG = """
        INSERT DATA {
            <urn:childOf> owl:inverseOf <urn:hasChild> .
            graph <urn:family> {
                <urn:John> <urn:childOf> <urn:Mary>
            }
        }
    """.trimIndent()

internal val addMary = """
        INSERT DATA {
            <urn:childOf> owl:inverseOf <urn:hasChild> .
            <urn:John> <urn:childOf> <urn:Mary>.
        }
    """.trimIndent()

internal val explainMary = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX proof: <http://www.ontotext.com/proof/>
        SELECT ?rule ?s ?p ?o ?context WHERE {
            VALUES (?subject ?predicate ?object) {(<urn:Mary> <urn:hasChild> <urn:John>)}
            ?ctx proof:explain (?subject ?predicate ?object) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

internal val explainMaryNg = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX proof: <http://www.ontotext.com/proof/>
        SELECT ?rule ?s ?p ?o ?context WHERE {
            VALUES (?subject ?predicate ?object) {(<urn:Mary> <urn:hasChild> <urn:John>)}
            ?ctx proof:explain (?subject ?predicate ?object <urn:family>) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

internal val explainMaryExplicit = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX proof: <http://www.ontotext.com/proof/>
        SELECT ?rule ?s ?p ?o ?context WHERE {
            VALUES (?subject ?predicate ?object) {(<urn:John> <urn:childOf> <urn:Mary>)}
            ?ctx proof:explain (?subject ?predicate ?object) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

internal val registerLNameFn = """
        PREFIX jsfn:<http://www.ontotext.com/js#>
        INSERT DATA {
            [] jsfn:register '''
            function lname(value) {
             if(value instanceof org.eclipse.rdf4j.model.IRI)
                 return value.getLocalName();
             else
                 return ""+value;
            }
        '''
        }
    """.trimIndent()

internal val explainMaryInSubject = """
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        PREFIX onto: <http://www.ontotext.com/>
        prefix proof: <http://www.ontotext.com/proof/>
        PREFIX jsfn: <http://www.ontotext.com/js#>
        
        SELECT (concat('(',jsfn:lname(?subject),',',jsfn:lname(?predicate),',',jsfn:lname(?object),')') as ?stmt)
            ?rule ?s ?p ?o ?context
        WHERE {
            bind(<urn:Mary> as ?subject) .
            {?subject ?predicate ?object}

            ?ctx proof:explain (?subject ?predicate ?object) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
    """.trimIndent()

internal val addWine = """
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    INSERT data {
        <urn:Red> a <urn:Colour> .
        <urn:White> a <urn:Colour> .
        <has:color> a rdf:Property .
        <urn:WhiteThing> a owl:Restriction;
                        owl:onProperty <has:color>;
                        owl:hasValue <urn:White> .
        <urn:RedThing> a owl:Restriction;
                        owl:onProperty <has:color>;
                        owl:hasValue <urn:Red> .
        <has:component> a rdf:Property .
        <urn:Wine> a owl:Restriction;
                        owl:onProperty <has:component>;
                        owl:someValuesFrom <urn:Grape> .
        <urn:RedWine> owl:intersectionOf (<urn:RedThing> <urn:Wine>) .
        <urn:WhiteWine> owl:intersectionOf (<urn:WhiteThing> <urn:Wine>) .
        <urn:Beer> a owl:Restriction;
                        owl:onProperty <has:component>;
                        owl:someValuesFrom <urn:Malt> .
        <urn:PilsenerMalt> a <urn:Malt> .
        <urn:PaleMalt> a <urn:Malt> .
        <urn:WheatMalt> a <urn:Malt> .
    
        <urn:MerloGrape> a <urn:Grape> .
        <urn:CaberneGrape> a <urn:Grape> .
        <urn:MavrudGrape> a <urn:Grape> .
    
        <urn:Merlo> <has:component> <urn:MerloGrape> ;
                    <has:color> <urn:Red> .
    }
""".trimIndent()

internal val registerStmtFn = """
    PREFIX jsfn:<http://www.ontotext.com/js#>
    INSERT DATA {
        [] jsfn:register '''
        function stmt(s, p, o, c) {
         return '('+lname(s)+', '+lname(p)+', '+lname(o)+(c?', '+lname(c):'')+')';
        }
    '''
    }
""".trimIndent()

internal val registerBNodeFn = """
    PREFIX jsfn:<http://www.ontotext.com/js#>
    INSERT DATA {
        [] jsfn:register '''
        function _bnode(value) {
            return org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance().createBNode(value);
        }
    '''
    }
""".trimIndent()

internal val explainMerlotTypeRedWine = """
    PREFIX jsfn:<http://www.ontotext.com/js#>
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    prefix proof: <http://www.ontotext.com/proof/>
    SELECT ?rule ?s ?p ?o ?context
    WHERE {
        {
                VALUES (?subject ?predicate ?object) {
                        (<urn:Merlo> rdf:type <urn:RedWine>)
                }
    
        }
        ?ctx proof:explain (?subject ?predicate ?object) .
        ?ctx proof:rule ?rule .
        ?ctx proof:subject ?s .
        ?ctx proof:predicate ?p .
        ?ctx proof:object ?o .
        ?ctx proof:context ?context .
    }
""".trimIndent()

internal val describeMerlo = "DESCRIBE <urn:Merlo> "

internal val registeredFns = """
    PREFIX jsfn:<http://www.ontotext.com/js#>
    SELECT ?s ?o {
        ?s jsfn:enum ?o
    }
""".trimIndent()


internal fun explain(subject: String, predicate: String, `object`: String, context: String = "") = """
        PREFIX : <http://www.example.com/>
        PREFIX t: <http://www.example.com/tbox/>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        PREFIX owl: <http://www.w3.org/2002/07/owl#>
        prefix proof: <http://www.ontotext.com/proof/>
        SELECT ?rule ?s ?p ?o ?context WHERE {
            VALUES (?subject ?predicate ?object) {($subject $predicate $`object`)}
            ?ctx proof:explain (?subject ?predicate ?object $context) .
            ?ctx proof:rule ?rule .
            ?ctx proof:subject ?s .
            ?ctx proof:predicate ?p .
            ?ctx proof:object ?o .
            ?ctx proof:context ?context .
        }
""".trimIndent()

internal val explainFood =
    "PREFIX pr: <http://www.ontotext.com/proof/>\r\nPREFIX food: <http://www.w3.org/TR/2003/PR-owl-guide-20031209/food#>\r\nPREFIX onto: <http://www.ontotext.com/>\r\n\r\nselect ?ctx ?s ?p ?o ?rule ?context ?subj ?pred ?obj\r\nfrom named onto:implicit \r\nfrom named onto:explicit \r\n{\r\n#		values (?s ?p ?o) {(food:Fruit UNDEF UNDEF)} \r\n		graph ?g {?s ?p ?o} \r\n	filter(strstarts(str(?s),str(food:)))\r\n     ?ctx pr:explain (?s ?p ?o) .\r\n     ?ctx pr:rule ?rule .\r\n     ?ctx pr:subject ?subj .\r\n     ?ctx pr:predicate ?pred .\r\n     ?ctx pr:object ?obj .\r\n     ?ctx pr:context ?context .\r\n}\r\n"

