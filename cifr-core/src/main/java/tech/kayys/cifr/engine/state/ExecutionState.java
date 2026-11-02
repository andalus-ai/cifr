package tech.kayys.cifr.engine.state;

import tech.kayys.cifr.api.agent.AgentPhase;

import java.time.Instant;
import java.util.Map;

public record ExecutionState(
    String executionId,
    AgentPhase phase,
    int attempt,
    String checkpointId,
    String lastEventId,
    long inputTokens,
    long outputTokens,
    Instant startedAt,
    Instant updatedAt,
    Map<String, Object> metadata
) {}
