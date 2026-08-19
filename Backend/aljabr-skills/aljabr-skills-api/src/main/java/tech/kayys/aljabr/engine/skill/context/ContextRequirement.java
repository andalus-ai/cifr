package tech.kayys.aljabr.engine.skill.context;

import java.util.Set;

public record ContextRequirement(
    Set<ContextSource> required,
    Set<ContextSource> optional,
    ContextCompressionStrategy strategy,
    int preferredTokens
) {
    public ContextRequirement {
        required = required != null ? Set.copyOf(required) : Set.of();
        optional = optional != null ? Set.copyOf(optional) : Set.of();
        strategy = strategy != null ? strategy : ContextCompressionStrategy.MINIMAL_RELEVANT;
    }

    public static ContextRequirement minimal(Set<ContextSource> required) {
        return new ContextRequirement(required, Set.of(), ContextCompressionStrategy.MINIMAL_RELEVANT, 2048);
    }
}
