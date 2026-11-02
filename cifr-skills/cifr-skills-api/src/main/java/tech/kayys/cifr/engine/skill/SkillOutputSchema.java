package tech.kayys.cifr.engine.skill;

import java.util.Map;

public record SkillOutputSchema(Map<String, Object> properties) {
    public SkillOutputSchema {
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    public static SkillOutputSchema empty() {
        return new SkillOutputSchema(Map.of());
    }

    public static SkillOutputSchema of(Map<String, Object> properties) {
        return new SkillOutputSchema(properties);
    }
}
