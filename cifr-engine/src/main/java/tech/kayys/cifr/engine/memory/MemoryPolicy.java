package tech.kayys.cifr.engine.memory;

public record MemoryPolicy(
    MemoryScope scope,
    double importance,
    boolean isPermanent,
    boolean isSensitive
) {}
