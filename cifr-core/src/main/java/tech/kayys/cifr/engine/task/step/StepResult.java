package tech.kayys.cifr.engine.task.step;

import java.util.List;

public record StepResult(StepId stepId, boolean success, String output, List<String> evidenceIds) {
    public StepResult {
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }

    public static StepResult success(StepId id, String output) {
        return new StepResult(id, true, output, List.of());
    }

    public static StepResult failure(StepId id, String reason) {
        return new StepResult(id, false, reason, List.of());
    }
}
