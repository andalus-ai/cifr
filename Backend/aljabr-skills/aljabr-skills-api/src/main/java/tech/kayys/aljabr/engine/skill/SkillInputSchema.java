package tech.kayys.aljabr.engine.skill;

import java.util.Map;

public record SkillInputSchema(Map<String, Object> properties) {
    public SkillInputSchema {
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    public static SkillInputSchema empty() {
        return new SkillInputSchema(Map.of());
    }

    public static SkillInputSchema of(Map<String, Object> properties) {
        return new SkillInputSchema(properties);
    }
}
