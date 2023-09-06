package situatedInference

import com.ontotext.trree.sdk.Entities.Scope.REQUEST
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.util.Values.triple


/** For every statement E P O, or S P E, where E is the iri of an ExpandableContext,
 *  at query time, statements <<s p o> P O and S P << s p o>> will be generated where s p o is a triple in E
 * */
interface Expandable : Quotable {
    fun getExpansionsInSubject(): ContextWithStorage

    fun getExpansionsInObject(): ContextWithStorage

    fun getExpansions(): ContextWithStorage
}


class ExpandableContext(
    override val sourceId: Long,
    override val quotableId: Long,
    private val requestContext: SituatedInferenceContext
) : ContextWithStorage(), Expandable, Quotable by QuotableImpl(sourceId, quotableId, requestContext) {

    private val entities = requestContext.repositoryConnection.entityPoolConnection.entities

    //TODO should be fetching from everywhere to get quoting statements
    override fun getExpansionsInSubject(): ContextWithStorage {
        val reifiedStatementsAsQuoted =
            requestContext.repositoryConnection.getStatements(sourceId, 0, 0, 0).asSequence().map { quotingQuad ->
                getAll().map { quad ->
                    val tripleId = entities.put(
                        triple(
                            entities[quad.subject] as Resource,
                            entities[quad.predicate] as IRI,
                            entities[quad.`object`]
                        ),
                        REQUEST
                    )
                    quotingQuad.replaceValues(sourceId, tripleId)
                }
            }.flatten()
        return fromSequence(reifiedStatementsAsQuoted)
    }

    override fun getExpansionsInObject(): ContextWithStorage {
        val reifiedStatementsAsQuoted =
            requestContext.repositoryConnection.getStatements(0, 0, sourceId, 0).asSequence().map { quotingQuad ->
            getAll().map { quad ->
                val tripleId = entities.put(
                    triple(
                        entities[quad.subject] as Resource,
                        entities[quad.predicate] as IRI,
                        entities[quad.`object`]
                    ),
                    REQUEST
                )
                quotingQuad.replaceValues(sourceId, tripleId)
            }
        }.flatten()
        return fromSequence(reifiedStatementsAsQuoted)
    }


    override fun getExpansions(): ContextWithStorage = fromSequence(
        getExpansionsInSubject().getAll() + getExpansionsInObject().getAll()
    )
}