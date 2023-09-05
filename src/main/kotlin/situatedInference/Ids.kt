package situatedInference

import com.ontotext.trree.sdk.Entities
import com.ontotext.trree.sdk.PluginConnection
import org.eclipse.rdf4j.model.util.Values
import org.eclipse.rdf4j.model.vocabulary.RDF
import kotlin.properties.Delegates

internal var explainId by Delegates.notNull<Long>()
internal var situateId by Delegates.notNull<Long>()
internal var sharedId by Delegates.notNull<Long>()
internal var situateInsideId by Delegates.notNull<Long>()
internal var situatedContextPrefixId by Delegates.notNull<Long>()
internal var sharedKnowledgeContextId by Delegates.notNull<Long>()
internal var situatedContextId by Delegates.notNull<Long>()
internal var situateSchemaId by Delegates.notNull<Long>()
internal var appendToContextsId by Delegates.notNull<Long>()
internal var hasSituatedContextId by Delegates.notNull<Long>()
internal var prefixToSituateId by Delegates.notNull<Long>()
internal var regexToSituateId by Delegates.notNull<Long>()

internal var rdfSubjectId by Delegates.notNull<Long>()
internal var rdfPredicateId by Delegates.notNull<Long>()
internal var rdfObjectId by Delegates.notNull<Long>()
internal var rdfContextId by Delegates.notNull<Long>()


internal var asTripleId by Delegates.notNull<Long>()
internal var asSingletonId by Delegates.notNull<Long>()
internal var reifiesGraphId by Delegates.notNull<Long>()
internal var graphFromEmbeddedId by Delegates.notNull<Long>()
internal var testBlankId by Delegates.notNull<Long>()
internal var groupsTripleId by Delegates.notNull<Long>()


class IriStorage(pluginConnection: PluginConnection) {

    private val namespace = "https://w3id.org/conjectures/"
    private val schemasNamespace = namespace + "schemas"
    private val explainIri = Values.iri(namespace + "explain")
    private val situateIri = Values.iri(namespace + "situate")
    private val sharedIri = Values.iri(namespace + "shared")
    private val situateInsideIri = Values.iri(namespace + "situateInside")
    private val situatedContextPrefixIri = Values.iri(namespace + "SituatedContextPrefix")
    private val sharedKnowledgeContextIri = Values.iri(namespace + "SharedKnowledgeContext")
    private val situatedContextIri = Values.iri(namespace + "SituatedContext")
    private val situateSchemaIri = Values.iri(namespace + "situateSchema")
    private val appendToContextsIri = Values.iri(namespace + "appendToContexts")
    private val hasSituatedContextIri = Values.iri(namespace + "hasSituatedContext")
    private val prefixToSituateIri = Values.iri(namespace + "prefixToSituate")
    private val regexToSituateIri = Values.iri(namespace + "regexToSituate")
    private val asTripleIri = Values.iri(namespace + "asTriple")
    private val asSingletonIri = Values.iri(namespace + "asSingleton")
    private val reifiesGraphIri = Values.iri(namespace + "reifiesGraph")
    private val graphFromEmbeddedIri = Values.iri(namespace + "graphFromEmbedded")

    init {
        explainId = pluginConnection.entities.put(explainIri, Entities.Scope.SYSTEM)
        situateId = pluginConnection.entities.put(situateIri, Entities.Scope.SYSTEM)
        sharedId = pluginConnection.entities.put(sharedIri, Entities.Scope.SYSTEM)
        situateInsideId = pluginConnection.entities.put(situateInsideIri, Entities.Scope.SYSTEM)
        situatedContextPrefixId = pluginConnection.entities.put(situatedContextPrefixIri, Entities.Scope.SYSTEM)
        sharedKnowledgeContextId = pluginConnection.entities.put(sharedKnowledgeContextIri, Entities.Scope.SYSTEM)
        situatedContextId = pluginConnection.entities.put(situatedContextIri, Entities.Scope.SYSTEM)
        situateSchemaId = pluginConnection.entities.put(situateSchemaIri, Entities.Scope.SYSTEM)
        appendToContextsId = pluginConnection.entities.put(appendToContextsIri, Entities.Scope.SYSTEM)
        hasSituatedContextId = pluginConnection.entities.put(hasSituatedContextIri, Entities.Scope.SYSTEM)
        prefixToSituateId = pluginConnection.entities.put(prefixToSituateIri, Entities.Scope.SYSTEM)
        regexToSituateId = pluginConnection.entities.put(regexToSituateIri, Entities.Scope.SYSTEM)

        rdfSubjectId = pluginConnection.entities.put(RDF.SUBJECT, Entities.Scope.SYSTEM)
        rdfPredicateId = pluginConnection.entities.put(RDF.PREDICATE, Entities.Scope.SYSTEM)
        rdfObjectId = pluginConnection.entities.put(RDF.OBJECT, Entities.Scope.SYSTEM)
        asTripleId = pluginConnection.entities.put(asTripleIri, Entities.Scope.SYSTEM)
        asSingletonId = pluginConnection.entities.put(asSingletonIri, Entities.Scope.SYSTEM)
        reifiesGraphId = pluginConnection.entities.put(reifiesGraphIri, Entities.Scope.SYSTEM)
        graphFromEmbeddedId = pluginConnection.entities.put(graphFromEmbeddedIri, Entities.Scope.SYSTEM)
    }


}