package situatedInference

import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.entitypool.EntityPoolConnection
import com.ontotext.trree.entitypool.PluginEntitiesAdapter
import com.ontotext.trree.sdk.Entities
import com.ontotext.trree.sdk.PluginException
import org.eclipse.rdf4j.model.util.Values.iri
import kotlin.properties.Delegates


class SituateTask(private val requestContext: SituatedInferenceContext) {

    private val entities: PluginEntitiesAdapter = requestContext.repositoryConnection.entityPoolConnection.entities

    var schemaId by Delegates.notNull<Long>()
    var schema: SchemaForSituate? = null

    var suffixForNewNames: String = "-situated"

    var createdSituationsIds = mutableSetOf<Long>()

    private fun situationsAreCreated(): Boolean {
        return schema?.contextsToSituate == createdSituationsIds
    }


    private fun createSituationOfContext(contextId: Long): SituatedContext {
        val schema = this.schema ?: throw PluginException("You are trying to situate a schema that you haven't bound")
        val name =
            schema.contextToNameForSituation[contextId] ?: (entities[contextId].stringValue() + suffixForNewNames)
        val newSituationId = requestContext.repositoryConnection.transaction { repositoryConnection ->
            repositoryConnection.entityPoolConnection.transaction {
                it.entities.put(iri(name), Entities.Scope.REQUEST)
            }
        } //TODO remove transaction

        val sourceId = (requestContext.inMemoryContexts[contextId] as? Quotable)?.sourceId ?: contextId
        return SituatedContext(newSituationId, sourceId, schema.sharedContexts, requestContext)
    }

    fun createSituations() {
        (schema!!.contextsToSituate - createdSituationsIds).forEach { contextId ->
            val situation = createSituationOfContext(contextId)
            requestContext.inMemoryContexts[situation.quotableId] = situation
            createdSituationsIds.add(situation.quotableId)
        }
    }
}

fun <R> AbstractRepositoryConnection.transaction(block: (AbstractRepositoryConnection) -> R): R {
    try {
        this.beginTransaction()
        this.precommit()
        val result = block(this)
        this.commit()
        return result
    } catch (e: Exception) {
        this.rollback()
        throw e
    }
}

fun <R> EntityPoolConnection.transaction(block: (EntityPoolConnection) -> R): R {
    try {
        this.beginExclusive()
        this.precommit()
        val result = block(this)
        this.commit()
        return result
    } catch (e: Exception) {
        this.rollback()
        throw e
    }
}