package tech.kayys.cifr.engine.execution;

/**
 * Unique identifier for an agent execution instance.
 */
public record ExecutionId(String value) {
    public static ExecutionId of(String value) {
        return new ExecutionId(value);
    }

    public static ExecutionId generate() {
        return new ExecutionId("exec-" + java.util.UUID.randomUUID());
    }
}
