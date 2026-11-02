package tech.kayys.cifr.engine.skill;

import java.util.Set;

public record SkillCapabilityPolicy(
    Set<String> required,
    Set<String> optional,
    Set<String> forbidden
) {
    public SkillCapabilityPolicy {
        required = required != null ? Set.copyOf(required) : Set.of();
        optional = optional != null ? Set.copyOf(optional) : Set.of();
        forbidden = forbidden != null ? Set.copyOf(forbidden) : Set.of();
    }

    public static SkillCapabilityPolicy of(Set<String> required) {
        return new SkillCapabilityPolicy(required, Set.of(), Set.of());
    }
}
