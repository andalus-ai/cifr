package tech.kayys.aljabr.engine.skill.telemetry;

import tech.kayys.aljabr.engine.skill.reasoning.ReasoningDecision;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record SkillExecutionRecord(
    String executionId,
    String skillId,
    Instant startedAt,
    Instant completedAt,
    long durationMs,
    boolean success,
    ReasoningDecision decision,
    List<String> evidenceIds
) {
    public SkillExecutionRecord {
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }

    public Duration duration() {
        return Duration.ofMillis(durationMs);
    }
}
