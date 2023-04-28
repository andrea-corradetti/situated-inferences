import com.ontotext.trree.TransactionListener
import com.ontotext.trree.sdk.*
import com.ontotext.trree.sdk.impl.RepositorySettings
import com.ontotext.trree.sdk.impl.StatementsImpl
import com.ontotext.trree.sdk.impl.StatementsRequestImpl
import jdk.jfr.StackTrace
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.util.Statements.statement
import org.eclipse.rdf4j.model.util.Values.iri
import org.eclipse.rdf4j.repository.Repository
import org.slf4j.Logger
import java.io.File

const val UNBOUND_VARIABLE = 0
const val ANY = 0L

class SituatedInference : Plugin, Preprocessor, ContextUpdateHandler {
    private val prefix = "http://example.com/"
    private val conjContext = prefix + "conj"
    private val conjContextIRI = iri(conjContext)
    private val conj = "https://w3id.org/conjectures/"
    private val registerPredicateIRI = iri(prefix + "registerPredicate")
    private val loisLaneThoughts = iri("${conj}LoisLaneThoughts")
    private val marthaKentsThoughts = iri("${conj}MarthaKentsThoughts")
    private val myThoughts = iri("${conj}myThoughts")


    private val predicatesToListenFor = mutableListOf<Long>()
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
        val conjContextId = pluginConnection.entities.put(conjContextIRI, Entities.Scope.SYSTEM)
        val registerPredicateId = pluginConnection.entities.put(registerPredicateIRI, Entities.Scope.SYSTEM)
        pluginConnection.properties.
        predicatesToListenFor.add(registerPredicateId)
        logger?.warn("this is printing something")

    }

    override fun setFingerprint(p0: Long) {
//        TODO("Not yet implemented")
    }

    override fun getFingerprint(): Long {
        return 1L
//        TODO("Not yet implemented")
    }

    override fun shutdown(p0: ShutdownReason?) {
//        TODO("Not yet implemented")
    }

    override fun preprocess(request: Request?): RequestContext? { //this is only for query processing and not update processing!

        val context = Context()
        context.request = request
        if ((request is QueryRequest) && request.dataset != null) { //FIXME questo ha dato un null error su insert
            if (request.dataset.namedGraphs.contains(conjContextIRI)) {
                logger?.info("preprocess triggerato da richiesta sul graph.")
                return context
            }
        }
        return null

    }


    override fun getUpdateContexts(): Array<Resource> {
        Thread.currentThread().stackTrace.forEach { println(it) }

        return arrayOf(conjContextIRI, loisLaneThoughts, marthaKentsThoughts, myThoughts)
    }

    override fun handleContextUpdate(
        subject: Resource?,
        predicate: IRI?,
        obj: Value?,
        context: Resource?,
        isAddition: Boolean,
        pluginConnection: PluginConnection
    ) {
        println(context?.stringValue())
        if (context == null) return
        val repository = repoStore.getRepositoryForContext(context)
        val statement = repository.valueFactory.createStatement(subject, predicate, obj, context)

        println(statement)

        val connection = repository.connection
        try {
            connection.add(statement)
            connection.getStatements(null, null, null).forEach() { println(it) }
        } finally {
            connection.close()
        }
//        val contextId = pluginConnection.entities.resolve(context)

    }

//    override fun transactionStarted(p0: PluginConnection?) {
//    }

//    override fun transactionCommit(pluginConnection: PluginConnection?) {
//        isUpdatingContext = true
//        try {
//            logger?.info("isAdd allowed? ${pluginConnection?.repository?.isAddAllowed}")
//            logger?.info(statementsToAdd.toString())
//            if (statementsToAdd.isNotEmpty() && pluginConnection?.repository?.isAddAllowed == true) {
//                logger?.info("Im in :P")
//                statementsToAdd.forEach {
//                    pluginConnection?.repository?.addStatement(it.subject, it.predicate, it.`object`, it.context)
//                }
//            }
//        } finally {
//            isUpdatingContext = false
//        }
//    }

//    override fun transactionCompleted(p0: PluginConnection?) {
//    }
//
//    override fun transactionAborted(p0: PluginConnection?) {
//    }

//    override fun getPredicatesToListenFor(): LongArray {
//        return predicatesToListenFor.toLongArray()
//    }

//    override fun interpretUpdate(
//        subject: Long,
//        predicate: Long,
//        obj: Long,
//        context: Long,
//        isAddition: Boolean,
//        isExplicit: Boolean,
//        pluginConnection: PluginConnection?
//    ): Boolean {
//        if (pluginConnection == null) return false
//
//        pluginConnection.properties.getRepositorySetting("")
//
//        val entities = pluginConnection.entities;
//        when (predicate) {
//            entities.resolve(registerPredicateIRI) -> registerPredicateId(obj)
//        }
//
//    }
//
//    private fun registerPredicateId(id: Long) {
//
//    }
}
