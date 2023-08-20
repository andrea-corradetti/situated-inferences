package situatedInference

import com.ontotext.trree.sdk.PluginException

class PluginConsistencyException(message: String?) : PluginException("Consistency Exception: $message")

