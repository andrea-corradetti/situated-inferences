package situatedInference;

import java.util.ArrayList;
import java.util.List;

public class Solution {
    String rule;
    List<long[]> premises;

    public String getRule() {
        return rule;
    }

    public List<long[]> getPremises() {
        return premises;
    }

    Solution(String rule, List<long[]> premises) {
        this.rule = rule;
        this.premises = premises;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("rule:").append(rule).append("\n");
        for (long[] p : premises) {
            builder.append(p[0]).append(",").append(p[1]).append(",");
            builder.append(p[2]).append(",").append(p[3]).append("\n");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Solution solution = (Solution) o;

        if (!rule.equals(solution.rule)) return false;
        return premises.equals(solution.premises);
    }

    @Override
    public int hashCode() {
        int result = rule.hashCode();
        result = 31 * result + premises.hashCode();
        return result;
    }
}
