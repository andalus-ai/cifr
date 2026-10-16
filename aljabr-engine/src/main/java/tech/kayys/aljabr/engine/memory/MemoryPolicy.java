package tech.kayys.aljabr.engine.memory;

public record MemoryPolicy(
    MemoryScope scope,
    double importance,
    boolean isPermanent,
    boolean isSensitive
) {}
