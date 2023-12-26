package situatedInference

/**
 * A reified context should contain only statements resulting from a reification.
 * As such, it is expected that for every statement S P O in the context,
 * S be of type rdf:Statement
 * Thus, the operation of substituting the graph uri with S in every quoting triple should result
 * in internally consistent triples.
 *
 **/
class ReifiedContext(reifiedContextId: Long, reificationId: Long, private val requestContext: SituatedInferenceContext) :
    ContextWithStorage(),
    Quotable by QuotableImpl(reifiedContextId, reificationId, requestContext),
    Reified {
    override fun getQuotingInnerStatementsAsSubject(): ContextWithStorage {
        val reifiedStatementsAsQuoted = requestContext.repositoryConnection.getStatements(sourceId, 0, 0, 0).asSequence().map { quotingQuad ->
            getAll().map { reifiedQuad -> quotingQuad.replaceValues(sourceId, reifiedQuad.subject) }
        }.flatten()
        return fromSequence(reifiedStatementsAsQuoted)
    }

    override fun getQuotingInnerStatementsAsObject(): ContextWithStorage {
        val reifiedStatementsAsQuoted = requestContext.repositoryConnection.getStatements(0, 0, sourceId, 0).asSequence().map { quotingQuad ->
            getAll().map { reifiedQuad -> quotingQuad.replaceValues(sourceId, reifiedQuad.subject) }
        }.flatten()
        return fromSequence(reifiedStatementsAsQuoted)
    }

    override fun getQuotingInnerStatement(): ContextWithStorage {
        val reifiedStatementsAsQuotedInSubject = requestContext.repositoryConnection.getStatements(sourceId, 0, 0, 0).asSequence().map { quotingQuad ->
            getAll().map { reifiedQuad -> quotingQuad.replaceValues(sourceId, reifiedQuad.subject) }
        }.flatten()

        val reifiedStatementsAsQuotedInObject = requestContext.repositoryConnection.getStatements(0, 0, sourceId, 0).asSequence().map { quotingQuad ->
            getAll().map { reifiedQuad -> quotingQuad.replaceValues(sourceId, reifiedQuad.subject) }
        }.flatten()

        return fromSequence((reifiedStatementsAsQuotedInSubject + reifiedStatementsAsQuotedInObject).distinct())
    }

    companion object {
        fun fromSequence(
            sequence: Sequence<Quad>,
            reifiedContextId: Long,
            reificationId: Long,
            requestContext: SituatedInferenceContext
        ) = ReifiedContext(reifiedContextId, reificationId, requestContext).apply {
            addAll(sequence.toList())
        }
    }

}