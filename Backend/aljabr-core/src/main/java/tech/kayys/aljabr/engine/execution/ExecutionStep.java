package tech.kayys.aljabr.engine.execution;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * An individual recorded step within an agent execution.
 */
public record ExecutionStep(
    String stepId,
    ExecutionId executionId,
    ExecutionStepType type,
    ExecutionState state,
    Instant startedAt,
    Optional<Instant> completedAt,
    Map<String, Object> details
) {
    public ExecutionStep {
        details = details != null ? Map.copyOf(details) : Map.of();
    }

    public static ExecutionStep start(ExecutionId execId, ExecutionStepType type, ExecutionState state) {
        return new ExecutionStep(
            "step-" + java.util.UUID.randomUUID(),
            execId,
            type,
            state,
            Instant.now(),
            Optional.empty(),
            Map.of()
        );
    }

    public ExecutionStep complete(Map<String, Object> outputDetails) {
        return new ExecutionStep(stepId, executionId, type, state, startedAt, Optional.of(Instant.now()), outputDetails);
    }
}
