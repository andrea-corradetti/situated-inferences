package situatedInference

data class ExplicitStatementProps(
    @JvmField val isExplicit: Boolean,
    @JvmField val explicitContext: Long,
    @JvmField val isDerivedFromSameAs: Boolean
)
