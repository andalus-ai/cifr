package tech.kayys.aljabr.engine.skill.routing;

import tech.kayys.aljabr.engine.skill.reasoning.ReasoningDecision;
import java.util.Map;

public record SkillSelection(
    String skillId,
    ReasoningDecision reasoningDecision,
    Map<String, Object> resolvedParameters,
    double confidence
) {
    public SkillSelection {
        resolvedParameters = resolvedParameters != null ? Map.copyOf(resolvedParameters) : Map.of();
    }
}
