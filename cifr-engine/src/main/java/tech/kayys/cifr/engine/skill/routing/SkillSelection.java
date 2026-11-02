package tech.kayys.cifr.engine.skill.routing;

import tech.kayys.cifr.engine.skill.reasoning.ReasoningDecision;
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
