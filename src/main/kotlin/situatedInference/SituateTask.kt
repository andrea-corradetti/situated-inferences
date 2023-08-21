package situatedInference

import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.StatementIdIterator
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

    fun findInBoundSituations(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long? = null
    ): StatementIdIterator {
        if (!situationsAreCreated()) {
            createSituations()
        }
        val statements = createdSituationsIds.asSequence().map {
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


    fun createSituationOfContext(contextId: Long): Situation  {
        val schema = this.schema ?: throw PluginException("You are trying to situate a schema that you haven't bound")
        val name = schema.contextToNameForSituation[contextId] ?: (entities[contextId].stringValue() + suffixForNewNames)
        val newSituationId = requestContext.repositoryConnection.transaction {
            entities.put(iri(name), Entities.Scope.REQUEST)
        }
        return Situation(requestContext, newSituationId, schema.sharedContexts + contextId)
    }

    fun createSituations() {
        (schema!!.contextsToSituate - createdSituationsIds).forEach { contextId ->
            val situation = createSituationOfContext(contextId)
            requestContext.situations[situation.id] = situation
            createdSituationsIds.add(situation.id)
        }
    }
}

fun <R> AbstractRepositoryConnection.transaction(block: (AbstractRepositoryConnection) -> R): R {
    try {
        this.beginTransaction()
        val result = block(this)
        this.precommit()
        this.commit()
        return result
    } catch (e: Exception) {
        this.rollback()
        throw e
    }
}