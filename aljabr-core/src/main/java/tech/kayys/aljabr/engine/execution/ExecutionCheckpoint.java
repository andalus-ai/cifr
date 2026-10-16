package tech.kayys.aljabr.engine.execution;

import java.time.Instant;
import java.util.Map;

/**
 * Checkpointed snapshot of an agent execution state for durable pause/resume.
 */
public record ExecutionCheckpoint(
    String checkpointId,
    ExecutionId executionId,
    ExecutionState state,
    int stepCount,
    Instant capturedAt,
    Map<String, Object> stateSnapshot
) {
    public ExecutionCheckpoint {
        stateSnapshot = stateSnapshot != null ? Map.copyOf(stateSnapshot) : Map.of();
    }

    public static ExecutionCheckpoint of(ExecutionId execId, ExecutionState state, int stepCount, Map<String, Object> snapshot) {
        return new ExecutionCheckpoint(
            "cp-" + java.util.UUID.randomUUID(),
            execId,
            state,
            stepCount,
            Instant.now(),
            snapshot
        );
    }
}
