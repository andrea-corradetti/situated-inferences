import com.ontotext.trree.plugin.sparqltemplate.SparqlTemplatePlugin
import com.ontotext.trree.sdk.PluginException
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream


class Configuration(private val file: String) {
    private val separator = ", "

    private var properties: Properties? = null
    fun initialize() {
        loadProperties()
    }

    fun save() {
        persistProperties()
    }

    private fun loadProperties() {
        val properties = Properties()
        val propertiesFile = File(file)
        if (propertiesFile.exists()) {
            try {
                Files.newBufferedReader(propertiesFile.toPath()).use { reader -> properties.load(reader) }
                this.properties = properties
            } catch (e: IOException) {
                throw PluginPropertiesNotInitializedException()
            }
        } else {
            fillWithDefaultValues()
            persistProperties()
        }
    }

    private fun fillWithDefaultValues() {
        setProperty(SituatedInferenceProperty.PREDICATES, "")
//        setValueCollection(SituatedInferenceProperty.CONTEXT, listOf(createIri))
    }

    /**
     * Persists the properties object to a file
     */
    private fun persistProperties() {

        try {
            Files.newBufferedWriter(Paths.get(file)).use { writer ->
                properties?.store(writer, "RDFRank properties") ?: throw PluginPropertiesNotInitializedException()
            }
        } catch (e: IOException) {
            throw PluginException("Can't load SituatedInference properties", e)
        }
    }

    private fun getProperty(property: SituatedInferenceProperty): String {
        return properties?.get(property.toString()).toString()
    }

    private fun setProperty(property: SituatedInferenceProperty, value: String) {
        properties?.setProperty(property.toString(), value)
            ?: throw PluginPropertiesNotInitializedException()
    }

    fun getValueCollection(property: SituatedInferenceProperty): List<IRI> {
        return getProperty(property).split(separator.toRegex()).filter { it.isNotBlank() }.map { VF.createIRI(it) }

//        return Stream
//            .of(property?.let { getProperty(it).split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray() })
//            .filter { iri -> !iri?.equals("") }
//            .map(SparqlTemplatePlugin.VF::createIRI)
//            .collect(Collectors.toList())
    }

    fun setValueCollection(property: SituatedInferenceProperty, value: Collection<Value>) {
        setProperty(
            property, value.joinToString(separator = separator) { it.stringValue() }
        )
    }


    enum class SituatedInferenceProperty(val propName: String) {
        PREDICATES("predicates"),
        CONTEXT("context")
    }


    companion object {
        private val VF: ValueFactory = SimpleValueFactory.getInstance()
    }
}

class PluginPropertiesNotInitializedException : PluginException("Properties not initialized correctly") {}