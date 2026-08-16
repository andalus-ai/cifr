package tech.kayys.aljabr.engine.skill.telemetry;

import tech.kayys.aljabr.engine.skill.routing.ReasoningDecision;

import java.time.Instant;
import java.util.List;

public record SkillExecutionRecord(
    String executionId,
    String skillId,
    Instant startedAt,
    Instant completedAt,
    long durationMs,
    boolean success,
    ReasoningDecision reasoningDecision,
    List<String> evidenceIds
) {
    public SkillExecutionRecord {
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }
}
