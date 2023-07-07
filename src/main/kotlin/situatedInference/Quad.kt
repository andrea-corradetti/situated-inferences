package situatedInference

data class Quad(
    @JvmField val subject: Long,
    @JvmField val predicate: Long,
    @JvmField val `object`: Long,
    @JvmField val context: Long = 0,
    @JvmField val status: Int = 0,

)
