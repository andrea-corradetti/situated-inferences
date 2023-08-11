package situatedInference

import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractInferencerTask
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.entitypool.EntityPoolConnection

class InjectedInferencer<in T : AbstractInferencer, in S : AbstractInferencerTask>(
    private val inferencer: T,
    private val task: S
) :
    AbstractInferencer by inferencer {
    override fun checkForInconsistencies(
        p0: EntityPoolConnection?,
        p1: Long,
        p2: Long,
        p3: Long,
        p4: Long,
        p5: Int
    ): String = inferencer.checkForInconsistencies(p0, p1, p2, p3, p4, p5)

    override fun doInference(p0: Long, p1: Long, p2: Long, p3: Long, p4: Int, p5: AbstractInferencerTask?) =
        task.doInference(p0, p1, p2, p3, p4, p5)

    override fun getRepStatements(p0: Long, p1: Long, p2: Long, p3: Int): StatementIdIterator =
        task.getRepStatements(p0, p1, p2, p3)

    override fun getRepStatements(p0: Long, p1: Long, p2: Long, p3: Long, p4: Int): StatementIdIterator =
        task.getRepStatements(p0, p1, p2, p3, p4)

    override fun ruleFired(p0: Long, p1: Long, p2: Long, p3: Long, p4: Int, p5: Int) =
        task.ruleFired(p0, p1, p2, p3, p4, p5)
}