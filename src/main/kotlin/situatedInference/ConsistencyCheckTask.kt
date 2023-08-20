package situatedInference

import com.ontotext.trree.AbstractInferencerTask
import com.ontotext.trree.StatementIdIterator
import org.slf4j.LoggerFactory

val consistencyCheckTask = object : AbstractInferencerTask {
    val logger = LoggerFactory.getLogger(this::class.java)

    override fun doInference(p0: Long, p1: Long, p2: Long, p3: Long, p4: Int, p5: AbstractInferencerTask?) {
//                TODO("Not yet implemented")
    }

    override fun ruleFired(p0: Long, p1: Long, p2: Long, p3: Long, p4: Int, p5: Int) {
//                TODO("Not yet implemented")
    }

    override fun getRepStatements(p0: Long, p1: Long, p2: Long, p3: Int): StatementIdIterator {
        logger.debug("it works!!!")
        return StatementIdIterator.empty
    }

    override fun getRepStatements(p0: Long, p1: Long, p2: Long, p3: Long, p4: Int): StatementIdIterator {
        logger.debug("it works!!!")
        return StatementIdIterator.empty
    }
}