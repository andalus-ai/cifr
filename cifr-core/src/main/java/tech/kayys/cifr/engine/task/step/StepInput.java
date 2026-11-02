package tech.kayys.cifr.engine.task.step;

import java.util.Map;

public record StepInput(Map<String, Object> parameters, String contextSnapshotId) {
    public StepInput {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    public static StepInput empty() {
        return new StepInput(Map.of(), null);
    }
}
