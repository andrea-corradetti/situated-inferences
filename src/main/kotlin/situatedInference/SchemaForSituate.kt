package situatedInference

import com.ontotext.trree.entitypool.PluginEntitiesAdapter
import com.ontotext.trree.sdk.Entities.UNBOUND
import org.eclipse.rdf4j.model.vocabulary.RDF
import kotlin.properties.Delegates

class SchemaForSituate(private val requestContext: SituatedInferenceContext) {
    var contextsToSituate = mutableSetOf<Long>()
    var sharedContexts = mutableSetOf<Long>()
    val contextToNameForSituation = mutableMapOf<Long, String>()
    val boundTasks = mutableSetOf<SituateTask>()

    var suffix by Delegates.observable("") { property, oldValue, newValue ->
        if (oldValue != newValue) {
            boundTasks.forEach { it.renameSituatedContexts() }
        }
    }



    private val entities: PluginEntitiesAdapter = requestContext.repositoryConnection.entityPoolConnection.entities

    fun parse(subjectId: Long, predicateId: Long, objectId: Long): Boolean {
        when {
            subjectId != UNBOUND && predicateId == entities.resolve(RDF.TYPE) && objectId == situatedContextId -> {
                contextsToSituate += subjectId
            }

            subjectId != UNBOUND && predicateId == entities.resolve(RDF.TYPE) && objectId == sharedKnowledgeContextId -> {
                sharedContexts += subjectId
            }

            subjectId != UNBOUND && predicateId == entities.resolve(RDF.TYPE) && objectId == prefixToSituateId -> {
                val prefix = entities[subjectId]?.stringValue() ?: return false
                val contextsWithPrefix =
                    requestContext.repositoryConnection.contextIDs.asSequence().map { it.context }
                        .filter { entities[it]?.stringValue()?.startsWith(prefix) == true }
                contextsToSituate += contextsWithPrefix
            }

            subjectId != UNBOUND && predicateId == entities.resolve(RDF.TYPE) && objectId == regexToSituateId -> {
                val regex = Regex(entities[subjectId]?.stringValue() ?: return false)
                val contextsWithPrefix =
                    requestContext.repositoryConnection.contextIDs.asSequence().map { it.context }
                        .filter { entities[it]?.stringValue()?.matches(regex) == true }.toList()
                contextsToSituate += contextsWithPrefix
            }

            subjectId != UNBOUND && predicateId == entities.resolve(RDF.TYPE) && objectId == appendToContextsId -> {
                suffix = entities[subjectId]?.stringValue() ?: return false
            }


            else -> return false
        }
        return true
    }

}