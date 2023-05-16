import com.ontotext.GraphDBConfigParameters
import com.ontotext.graphdb.Config
import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.RepositoryProperties
import com.ontotext.trree.sdk.*
import com.ontotext.trree.sdk.impl.RepositorySettings
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.util.Values.iri
import org.eclipse.rdf4j.query.BindingSet
import org.slf4j.Logger
import java.io.File


const val UNBOUND_VARIABLE = 0
const val ANY = 0L

class SituatedInference : Plugin, Preprocessor, ContextUpdateHandler, PatternInterpreter, Postprocessor {
    private val accessRepositoryConnection = "accessRepositoryConnection"
    private val accessInferencer = "accessInferencer"
    private val prefix = "http://example.com/"
    private val conjContext = prefix + "conj"
    private val conjContextIRI = iri(conjContext)
    private val conj = "https://w3id.org/conjectures/"

    //TODO move graph names and IRIs to config file
    private val registerPredicateIRI = iri(prefix + "registerPredicate")
    private val loisLaneThoughts = iri("${conj}LoisLaneThoughts")
    private val marthaKentsThoughts = iri("${conj}MarthaKentsThoughts")
    private val myThoughts = iri("${conj}myThoughts")

    private val namedGraphsToHandle =
        listOf<IRI>(loisLaneThoughts, marthaKentsThoughts, myThoughts) //TODO find a better name


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
        println(RepoStore.getAllRepositories())
//        val registerPredicateId = pluginConnection.entities.put(registerPredicateIRI, Entities.Scope.SYSTEM)
//        val repositoryIdIRI = iri("http://www.openrdf.org/config/repository#repositoryID")
//        val repositoryId = pluginConnection.properties.getRepositorySetting(null)
//        println(repositoryId)
//        predicatesToListenFor.add(registerPredicateId)
        logger?.warn("this is printing something")

//        pluginConnection.properties.getRepositorySetting()

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


    override fun getUpdateContexts(): Array<Resource> {
//        Thread.currentThread().stackTrace.forEach { println(it) }

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
        val repositoryForContext = RepoStore.getRepositoryForContext(context)
        val statement = repositoryForContext.valueFactory.createStatement(subject, predicate, obj, context)

        println(statement)

        val connection = repositoryForContext.connection
        try {
            connection.add(statement)
            connection.getStatements(null, null, null).forEach() { println(it) }
        } finally {
            connection.close()
        }
//        val contextId = pluginConnection.entities.resolve(context)

    }

    override fun preprocess(request: Request?): RequestContext? { //this is only for query processing and not update processing!
        if (request is QueryRequest) {
            val options = request.options
            if (options is SystemPluginOptions) {
                val connection =
                    options.getOption(SystemPluginOptions.Option.ACCESS_REPOSITORY_CONNECTION) as? AbstractRepositoryConnection
                val inferencer = options.getOption(SystemPluginOptions.Option.ACCESS_INFERENCER) as? AbstractInferencer
                return NamedInferenceContext().apply {
                    this.request = request
                    this.repositoryConnection = connection
                    this.inferencer = inferencer
                }
            }

//           val (conjGraphs, otherGraphs) = request.dataset.namedGraphs.filter { it.isIRI }.also { println(this) }
//                .partition { it.namespace.contains(Regex("conj:")) }
//
//            val dataset = SimpleDataset().apply {
//                request.dataset.defaultGraphs.forEach { iri -> addDefaultGraph(iri) }
//                otherGraphs.forEach { iri -> addNamedGraph(iri) }
//
//            }
//            return NamedInferenceContext().apply { this.request = request }

        }
        return null
    }

    override fun estimate(p0: Long, p1: Long, p2: Long, p3: Long, p4: PluginConnection?, p5: RequestContext?): Double {
        TODO("Not yet implemented")
    }


    override fun interpret(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: RequestContext?
    ): StatementIterator {
        if (requestContext !is NamedInferenceContext) return StatementIterator.FALSE();


//        val (subj, pred, obj) = arrayOf(subjectId, predicateId, objectId).map {pluginConnection.entities.get(it)}

        val obj = pluginConnection.entities.get(objectId)
        if (obj is IRI && obj in namedGraphsToHandle) {
            TODO("handle named graph as object in a statement")
        }


        //TODO I have to make a new model with inference disabled with the ground truth and the requested named graphs
        //TODO Afterwards, forward the query to this new model and return the statements
        val entities = pluginConnection.entities
//        val namedGraphsIds = namedGraphsToHandle.map { entities.resolve(it) }
//        val model = DynamicModelFactory().createEmptyModel();
//


//
//        val statementIterator = pluginConnection.statements.get(0, 0, 0)
//        while (statementIterator.next()) {
//            statementIterator.let {
//                val subject = entities.get(it.subject) as Resource
//                val predicate = entities.get(it.predicate) as IRI
//                val `object` = entities.get(it.`object`) as Value
//                val context = entities.get(it.context) as Resource
//                val statement = statement(subject, predicate, `object`, context)
//                model.add(statement)
//            }
//        }
//
//        namedGraphsToHandle.forEach { iri ->
//            val repo = RepoStore.getRepositoryForContext(iri)
//            val connection = repo.connection
//            connection.getStatements(null, null, null).forEach {
//                model.add(it)
//            }
//        }
//
//        val virtualRepo = SailRepository(NativeStore());
        return StatementIterator.FALSE()
    }

    override fun shouldPostprocess(requestContext: RequestContext?): Boolean {
        if (requestContext !is NamedInferenceContext)
            return false;


        val request = requestContext.request
        if (request is QueryRequest) {
            return request.dataset?.namedGraphs?.any { it in namedGraphsToHandle } ?: false //TODO remove the null check dataset should be defined
        }

        return false

    }

    override fun postprocess(bindingSet: BindingSet?, requestContext: RequestContext?): BindingSet {
        TODO("Not yet implemented")
    }

    override fun flush(p0: RequestContext?): MutableIterator<BindingSet> {
        TODO("Not yet implemented")
    }

}
