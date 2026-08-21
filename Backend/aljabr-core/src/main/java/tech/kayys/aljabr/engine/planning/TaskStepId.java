package tech.kayys.aljabr.engine.planning;

/**
 * Strongly typed identifier for a step in a TaskGraph.
 */
public record TaskStepId(String value) {
    public static TaskStepId of(String value) {
        return new TaskStepId(value);
    }

    public static TaskStepId generate() {
        return new TaskStepId("step-" + java.util.UUID.randomUUID());
    }
}
