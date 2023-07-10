package situatedInference

class Solution internal constructor(var rule: String, @JvmField var antecedents: List<Quad>) {

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("rule:").append(rule).append("\n")
        antecedents.forEach {
            builder.append(it.subject).append(",").append(it.predicate).append(",")
            builder.append(it.`object`).append(",").append(it.context).append("\n")
        }
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val solution = other as Solution
        return if (rule != solution.rule) false else antecedents == solution.antecedents
    }

    override fun hashCode(): Int {
        var result = rule.hashCode()
        result = 31 * result + antecedents.hashCode()
        return result
    }
}
