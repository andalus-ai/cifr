package tech.kayys.cifr.engine.debug;

import java.util.List;

public record DebugSession(
    DebugSessionId id,
    String taskId,
    FailureId failureId,
    List<Hypothesis> hypotheses,
    List<Experiment> experiments,
    List<String> evidenceIds,
    DebugState state
) {
    public DebugSession {
        hypotheses = hypotheses != null ? List.copyOf(hypotheses) : List.of();
        experiments = experiments != null ? List.copyOf(experiments) : List.of();
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }
}
