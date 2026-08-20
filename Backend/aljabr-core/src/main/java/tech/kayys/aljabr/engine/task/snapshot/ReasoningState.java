package tech.kayys.aljabr.engine.task.snapshot;

import java.util.List;
import java.util.Optional;

public record ReasoningState(
    List<String> hypotheses,
    List<String> decisions,
    List<String> uncertainties,
    Optional<String> activeHypothesis
) {
    public ReasoningState {
        hypotheses = hypotheses != null ? List.copyOf(hypotheses) : List.of();
        decisions = decisions != null ? List.copyOf(decisions) : List.of();
        uncertainties = uncertainties != null ? List.copyOf(uncertainties) : List.of();
        activeHypothesis = activeHypothesis != null ? activeHypothesis : Optional.empty();
    }

    public static ReasoningState empty() {
        return new ReasoningState(List.of(), List.of(), List.of(), Optional.empty());
    }
}
