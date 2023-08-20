package situatedInference

import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.entitypool.PluginEntitiesAdapter
import com.ontotext.trree.sdk.Entities
import org.eclipse.rdf4j.model.util.Values.iri
import kotlin.properties.Delegates


class SituateTask(private val requestContext: SituatedInferenceContext) {
    //    var contextsToSituate by Delegates.observable(setOf<Long>()) { _, oldValue, newValue ->
//        if (oldValue != newValue) {
//            isUpdated = false
//        }
//    }
//    var sharedContexts by Delegates.observable(setOf<Long>()) { _, oldValue, newValue ->
//        if (oldValue != newValue) {
//            isUpdated = false
//        }
//    }
    private val entities: PluginEntitiesAdapter = requestContext.repositoryConnection.entityPoolConnection.entities

    var schemaId by Delegates.notNull<Long>()
    private val schema
        get() = requestContext.schemas[schemaId]

    var suffixForNewNames: String? = null


    private var isUpdated = false
    var situationIds = mutableSetOf<Long>()

    fun findInBoundSituations(subjectId: Long, predicateId: Long, objectId: Long, contextId: Long? = null): StatementIdIterator {
        if (!isUpdated) {
            updateSituations()
        }
        val statements = situationIds.asSequence().map {
            requestContext.situations[it]
                ?.find(
                    subjectId,
                    predicateId,
                    objectId,
                    contextId //TODO consider changing with contextId
                )?.asSequence() ?: emptySequence()
        }.flatten()


        return statementIdIteratorFromSequence(statements)
    }


    fun updateSituations() {
        schema!!.contextsToSituate.forEach { contextId ->
            val contextName = entities.get(contextId)
            requestContext.repositoryConnection.beginTransaction()
            val newSituationId =
                entities.put(iri(contextName.stringValue() + suffixForNewNames), Entities.Scope.REQUEST)
            requestContext.repositoryConnection.commit()
            requestContext.situations[newSituationId] =
                Situation(
                    requestContext,
                    newSituationId,
                    setOf(contextId) + schema!!.sharedContexts
                ) //TODO can avoid assignment
            situationIds.add(newSituationId)
        }
        isUpdated = true
    }

}
