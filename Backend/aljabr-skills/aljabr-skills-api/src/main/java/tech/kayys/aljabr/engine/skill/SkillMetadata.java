package tech.kayys.aljabr.engine.skill;

import java.util.Set;
import tech.kayys.aljabr.engine.patch.RiskLevel;

public record SkillMetadata(
    String name,
    String description,
    SkillCategory category,
    RiskLevel risk,
    ReasoningMode reasoningMode,
    boolean mutatesWorkspace,
    Set<String> requiredCapabilities
) {
    public SkillMetadata {
        category = category != null ? category : SkillCategory.DISCOVERY;
        risk = risk != null ? risk : RiskLevel.LOW;
        reasoningMode = reasoningMode != null ? reasoningMode : ReasoningMode.NONE;
        requiredCapabilities = requiredCapabilities != null ? Set.copyOf(requiredCapabilities) : Set.of();
    }

    public static SkillMetadata of(String name, String description, SkillCategory category) {
        return new SkillMetadata(name, description, category, RiskLevel.LOW, ReasoningMode.NONE, false, Set.of());
    }
}
