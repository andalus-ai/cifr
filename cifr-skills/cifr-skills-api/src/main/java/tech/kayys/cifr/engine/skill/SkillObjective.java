package tech.kayys.cifr.engine.skill;

import java.util.List;

public record SkillObjective(String description, List<String> principles) {
    public SkillObjective {
        principles = principles != null ? List.copyOf(principles) : List.of();
    }

    public static SkillObjective of(String description, String... principles) {
        return new SkillObjective(description, List.of(principles));
    }
}
