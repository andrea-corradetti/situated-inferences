import com.ontotext.trree.sdk.*
import org.slf4j.Logger
import java.io.File

class SituatedInference : Plugin {

    private var logger: Logger? = null

    override fun getName(): String {
        return "situated-inferences"
    }

    override fun setDataDir(p0: File?) {
//        TODO("Not yet implemented")
    }

    override fun setLogger(p0: Logger?) {
        logger = p0
    }

    override fun initialize(p0: InitReason?, p1: PluginConnection?) {
//        TODO("Not yet implemented")
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

}