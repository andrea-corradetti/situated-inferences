package situatedInference

interface CheckableForConsistency {
    fun getInconsistencies(): Sequence<Quad>
}