package situatedInference

import org.eclipse.rdf4j.model.IRI

data class Singleton(val reifiedStatementIri: IRI, val singletonQuad: Quad)