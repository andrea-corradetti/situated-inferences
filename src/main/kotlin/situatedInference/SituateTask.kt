package situatedInference

import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.entitypool.EntityPoolConnection
import com.ontotext.trree.entitypool.PluginEntitiesAdapter
import com.ontotext.trree.sdk.Entities.Scope.REQUEST
import com.ontotext.trree.sdk.PluginException
import org.eclipse.rdf4j.model.util.Values.iri


class SituateTask(val taskId: Long, private val requestContext: SituatedInferenceContext) {

    private val entities: PluginEntitiesAdapter = requestContext.repositoryConnection.entityPoolConnection.entities

    var schemaId: Long? = null
    private val schema
        get() = schemaId?.let { requestContext.schemas[schemaId] }

    var alreadySituated = mutableSetOf<Long>()
    var createdSituationsIds = mutableSetOf<Long>()

    private fun situationsAreCreated(): Boolean {
        return schema?.contextsToSituate == alreadySituated
    }


    private fun createSituationOfContext(contextId: Long): SituatedContext {
        val schema = this.schema ?: throw PluginException("You are trying to situate a schema that you haven't bound")
        val name =
            schema.contextToNameForSituation[contextId] ?: (entities[contextId].stringValue() + schema.suffix)
        val newSituationId =
            requestContext.repositoryConnection.entityPoolConnection.entities.put(iri(name), REQUEST)


        val sourceId = (requestContext.inMemoryContexts[contextId] as? Quotable)?.sourceId ?: contextId
        return SituatedContext(
            newSituationId,
            sourceId,
            mainContextId = contextId,
            additionalContexts = schema.sharedContexts,
            requestContext
        ).apply { reset() }
    }

    fun createSituations() {
        (schema!!.contextsToSituate - alreadySituated).forEach { contextId ->
            val situation = createSituationOfContext(contextId)
            (requestContext.contextToSituatedContexts.getOrPut(contextId) { mutableSetOf() }) += situation.quotableId
            requestContext.inMemoryContexts[situation.quotableId] = situation
            createdSituationsIds.add(situation.quotableId)
            alreadySituated.add(contextId)
        }
    }

    fun createSituationsIfReady() {
        if (schema?.contextsToSituate?.all { requestContext.contextExists(it) } == true) {
            createSituations()
        }
    }

    fun renameSituatedContexts() {
        createdSituationsIds.toList().forEach { situationId ->
            val situation = requestContext.inMemoryContexts[situationId] as? SituatedContext ?: return
            val baseName = entities.get(situation.sourceId).stringValue()
            val newId = entities.put(iri(baseName + schema!!.suffix), REQUEST)
            requestContext.inMemoryContexts.remove(situationId)
            createdSituationsIds.remove(situationId)
            createdSituationsIds.add(newId)
            requestContext.inMemoryContexts[newId] = situation.apply { situatedContextId = newId }
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

fun SituatedInferenceContext.contextExists(contextId: Long): Boolean {
    return inMemoryContexts[contextId] != null || contextId in repositoryConnection.contextIDs.asSequence()
        .map { it.context }
}