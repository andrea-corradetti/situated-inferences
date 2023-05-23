import com.ontotext.trree.sdk.*
import org.eclipse.rdf4j.model.Triple
import org.eclipse.rdf4j.model.util.Values.iri
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.slf4j.Logger
import java.io.File
import kotlin.properties.Delegates


const val ANY = 0L


class SituatedInference : Plugin, StatementListener, PluginTransactionListener {
    private val shouldInferOnReification: Boolean = true    //TODO move to config file
    private val shouldInferOnRdfStar: Boolean = true
    private val shouldInferOnNamedGraphs: Boolean = true

    private val isDefaultGraphSharedKnowledge: Boolean = true

    private val conjBaseUri = "https://w3id.org/conjectures/"

    private val rdfSubject = iri(RDF.NAMESPACE + "subject")
    private val rdfPredicate = iri(RDF.NAMESPACE + "predicate")
    private val rdfObject = iri(RDF.NAMESPACE + "object")

    private var rdfSubjectId by Delegates.notNull<Long>()
    private var rdfPredicateId by Delegates.notNull<Long>()
    private var rdfObjectId by Delegates.notNull<Long>()
    private var reificationPredicates by Delegates.notNull<Array<Long>>()

    private val reifiedStatementsId = mutableSetOf<Long>() //REMEMBER TO EMPTY THIS BEFORE EVERY TRANSACTION

    private val quintuplesToAdd = mutableMapOf<String, Quintuple>()
    private val quintuplesToRemove = mutableListOf<Quintuple>()
    private val situatingStatements = mutableMapOf<String, MutableSet<Quintuple>>()

    private val reifiedRegex = Regex(".*/reified-\\d+")
    private val contextsRegex = arrayOf(reifiedRegex) //TODO add other regex

    private var logger: Logger? = null

    override fun getName(): String {
        return "situated-inferences2"
    }

    override fun setDataDir(p0: File?) {
//        TODO("Not yet implemented")
    }

    override fun setLogger(logger: Logger?) {
        this.logger = logger
    }

    override fun initialize(initReason: InitReason, pluginConnection: PluginConnection) {
        rdfSubjectId = pluginConnection.entities.put(rdfSubject, Entities.Scope.SYSTEM)
        rdfPredicateId = pluginConnection.entities.put(rdfPredicate, Entities.Scope.SYSTEM)
        rdfObjectId = pluginConnection.entities.put(rdfObject, Entities.Scope.SYSTEM)
        reificationPredicates =
            arrayOf(rdfSubjectId, rdfPredicateId, rdfObjectId) //TODO consider if adding rdf:statement
        logger?.info("reification predicates ${reificationPredicates.joinToString(",")}")
    }

    override fun setFingerprint(p0: Long) {
//        TODO("Not yet implemented")
    }

    override fun getFingerprint(): Long {
        return 1L //TODO choose an actual not made up fingerprint
    }

    override fun shutdown(p0: ShutdownReason?) {
//        TODO("Not yet implemented")
    }

    private fun getAntecedents(subject: Long, predicate: Long, obj: Long): Collection<Quintuple> {
        TODO("Not yet implemented")
    }

    //important using anonymous nodes (prefix "_:" ) will cause duplicate inserts because graphdb creates new nodes at each insert.
    override fun statementAdded(
        subject: Long,
        predicate: Long,
        obj: Long,
        context: Long,
        isExplicit: Boolean,
        pluginConnection: PluginConnection
    ): Boolean {
        if (shouldInferOnReification && predicate in reificationPredicates) {
            handleReifiedStatement(subject, predicate, obj, pluginConnection)
        }

        if (shouldInferOnRdfStar) {
            handleInferOnRdfStar(subject, predicate, obj, pluginConnection)
        }

        val contextValue = pluginConnection.entities.get(context)
        if (!isExplicit && contextsRegex.any {
                it matches contextValue?.stringValue().toString()
            }) { //TODO add other contexts to check
            val antecedents = getAntecedents(subject, predicate, obj)
            val contexts = getContexts(antecedents)

            if (isDefaultGraphSharedKnowledge) {
                if (contexts.count() == 1) {

                }

            } else {

            }

        }
        logger?.info("context id: $context")
        logger?.info("context string value ${contextValue.stringValue()}")




        return false //TODO check if this makes any difference
    }

    //FIXME fix this mess with keys and remove duplication.
    private fun handleInferOnRdfStar(
        subject: Long,
        predicate: Long,
        obj: Long,
        pluginConnection: PluginConnection
    ) {
        val objectValue = pluginConnection.entities.get(obj)
        if (objectValue.isTriple) {
            val objectTriple = objectValue as Triple
            val statementKey = makeKeyForQuotingTriple(subject, predicate, obj) + "objQuoted"
            val quotedStatementKey = makeKeyForQuotedTriple(objectTriple, pluginConnection)
            quintuplesToAdd.addQuotedTriple(statementKey, objectTriple, pluginConnection)
            situatingStatements.getOrPut(quotedStatementKey) { mutableSetOf() }
                .add(Quintuple(subject, predicate, obj))
            logger?.info("quintuplesToAdd[$statementKey] - ${quintuplesToAdd[statementKey]}")
            logger?.info("situatingStatements[$quotedStatementKey] - ${situatingStatements[quotedStatementKey]}")
        }


        val subjectValue = pluginConnection.entities.get(subject)
        if (subjectValue.isTriple) {
            val subjectTriple = subjectValue as Triple
            val statementKey = makeKeyForQuotingTriple(subject, predicate, obj) + "subjQuoted"
            val quotedStatementKey = makeKeyForQuotedTriple(subjectTriple, pluginConnection)
            quintuplesToAdd.addQuotedTriple(statementKey, subjectTriple, pluginConnection)
            situatingStatements.getOrPut(quotedStatementKey) { mutableSetOf() }
                .add(Quintuple(subject, predicate, obj))
            logger?.info("quintuplesToAdd[$statementKey] - ${quintuplesToAdd[statementKey]}")
            logger?.info("situatingStatements[$quotedStatementKey] - ${situatingStatements[quotedStatementKey]}")
        }
    }

    private fun getContexts(antecedents: Collection<Quintuple>): Collection<Long> {
        TODO("gotta implement this chief")
    }

    private fun makeKeyForQuotedTriple(triple: Triple, pluginConnection: PluginConnection): String {
        val quotedSubject = pluginConnection.entities.resolve(triple.subject)
        val quotedPredicate = pluginConnection.entities.resolve(triple.predicate)
        val quotedObject = pluginConnection.entities.resolve(triple.`object`)
        return "triple-$quotedSubject-$quotedPredicate-$quotedObject"
    }

    private fun MutableMap<String, Quintuple>.addQuotedTriple(
        statementKey: String, objectTriple: Triple, pluginConnection: PluginConnection
    ) {
        val contextUri = iri(conjBaseUri + "rs-$statementKey")

        this.putIfAbsent(statementKey, Quintuple())
        this[statementKey]!!.apply {
            this.subject = pluginConnection.entities.resolve(objectTriple.subject)
            this.predicate = pluginConnection.entities.resolve(objectTriple.predicate)
            this.obj = pluginConnection.entities.resolve(objectTriple.`object`)
            this.context = pluginConnection.entities.put(contextUri, Entities.Scope.SYSTEM)
        }
    }

    private fun makeKeyForQuotingTriple(subject: Long, pred: Long, obj: Long) = "triple-$subject-$pred-$obj"

    private fun handleReifiedStatement(subject: Long, predicate: Long, obj: Long, pluginConnection: PluginConnection) {
        val statementKey = makeKeyForReifiedStatement(subject)
        logger?.info("statement key $statementKey")

        val contextUri = iri(conjBaseUri + "reified-$subject")

        quintuplesToAdd.putIfAbsent(statementKey, Quintuple())
        quintuplesToAdd[statementKey]!!.context =
            pluginConnection.entities.put(contextUri, Entities.Scope.SYSTEM) //THIS IS A USELESS WRITE 2 TIMES OUT OF THREE
        when (predicate) {
            rdfSubjectId -> quintuplesToAdd[statementKey]!!.subject = obj
            rdfPredicateId -> quintuplesToAdd[statementKey]!!.predicate = obj
            rdfObjectId -> quintuplesToAdd[statementKey]!!.obj = obj
        }
        reifiedStatementsId.add(subject)
    }

    private fun makeKeyForReifiedStatement(subject: Long) = "statement-${subject}"

    private fun getSituatingStatementsFor(statementId: Long, pluginConnection: PluginConnection): List<Quintuple> {
        val statements = mutableListOf<Quintuple>()

        pluginConnection.statements.get(statementId, ANY, ANY).let { iter ->
            while (iter.next()) {
                val quintuple = Quintuple(iter.subject, iter.predicate, iter.`object`, iter.context, iter.isExplicit)
                logger?.info("subject is quoted: $quintuple")
                statements.add(quintuple)
            }
        }

        pluginConnection.statements.get(ANY, ANY, statementId).let { iter ->
            while (iter.next()) {
                val quintuple = Quintuple(iter.subject, iter.predicate, iter.`object`, iter.context, iter.isExplicit)
                logger?.info("object is quoted: $quintuple")
                statements.add(quintuple)
            }
        }

        return statements.filter { it.predicate !in reificationPredicates }
    }

    override fun statementRemoved(p0: Long, p1: Long, p2: Long, p3: Long, p4: Boolean, p5: PluginConnection?): Boolean {
        TODO("Not yet implemented")
    }

    override fun transactionStarted(p0: PluginConnection?) {
        logger?.info("TRANSACTION STARTED")
        reifiedStatementsId.clear()
    }

    override fun transactionCommit(pluginConnection: PluginConnection) {
        logger?.info("TRANSACTION COMMIT")
        reifiedStatementsId.forEach { statementId ->
            val situatingQuintuples: List<Quintuple> = getSituatingStatementsFor(statementId, pluginConnection)
            val statementKey = makeKeyForReifiedStatement(statementId)
            situatingStatements.getOrPut(statementKey) { mutableSetOf() }.addAll(situatingQuintuples)
        }
        //TODO add all situating statements
    }

    override fun transactionCompleted(p0: PluginConnection?) {
        logger?.info("TRANSACTION COMPLETED")
        logger?.info("situating statements: $situatingStatements}")

    }

    override fun transactionAborted(p0: PluginConnection?) {
        logger?.info("TRANSACTION ABORTED")

    }
}

data class Quintuple( //TODO consider new name
    var subject: Long? = null,
    var predicate: Long? = null,
    var obj: Long? = null,
    var context: Long? = null,
    var isExplicit: Boolean? = null,
)



