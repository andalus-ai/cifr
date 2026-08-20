package tech.kayys.aljabr.engine.skill;

import java.util.List;

public record SkillProjection(
    String objective,
    List<String> rules,
    String currentPhase,
    List<String> availableCapabilities,
    String outputContract
) {
    public SkillProjection {
        rules = rules != null ? List.copyOf(rules) : List.of();
        availableCapabilities = availableCapabilities != null ? List.copyOf(availableCapabilities) : List.of();
    }
}
