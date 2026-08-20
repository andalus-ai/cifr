package tech.kayys.aljabr.engine.policy;

import java.util.List;

/**
 * Policy decision specifying how model context should be assembled for an invocation.
 */
public record ContextDecision(
    ContextMode mode,
    int tokenBudget,
    List<String> requiredSources,
    boolean allowExpansion
) {
    public ContextDecision {
        requiredSources = requiredSources != null ? List.copyOf(requiredSources) : List.of();
    }

    public static ContextDecision focused(int tokenBudget) {
        return new ContextDecision(ContextMode.FOCUSED, tokenBudget, List.of(), true);
    }
}
