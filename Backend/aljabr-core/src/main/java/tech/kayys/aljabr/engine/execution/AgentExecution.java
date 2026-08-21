package tech.kayys.aljabr.engine.execution;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Durable execution record representing an active or completed agent run.
 */
public record AgentExecution(
    ExecutionId id,
    String taskId,
    ExecutionState state,
    Instant startedAt,
    Instant updatedAt,
    Optional<Instant> completedAt,
    Map<String, Object> metadata
) {
    public AgentExecution {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static AgentExecution create(String taskId, Map<String, Object> metadata) {
        Instant now = Instant.now();
        return new AgentExecution(
            ExecutionId.generate(),
            taskId,
            ExecutionState.CREATED,
            now,
            now,
            Optional.empty(),
            metadata
        );
    }

    public AgentExecution withState(ExecutionState newState) {
        Instant now = Instant.now();
        Optional<Instant> completed = newState.isTerminal() ? Optional.of(now) : this.completedAt;
        return new AgentExecution(id, taskId, newState, startedAt, now, completed, metadata);
    }
}
