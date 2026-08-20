package tech.kayys.aljabr.engine.policy;

import java.util.List;

/**
 * Audit trace of all rule evaluations leading to a final policy decision.
 */
public record DecisionTrace(
    String id,
    PolicyRequest request,
    List<Decision> evaluations,
    Decision finalDecision
) {
    public DecisionTrace {
        evaluations = evaluations != null ? List.copyOf(evaluations) : List.of();
    }
}
