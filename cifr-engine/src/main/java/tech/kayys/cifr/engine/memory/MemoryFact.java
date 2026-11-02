package tech.kayys.cifr.engine.memory;

import java.time.Instant;

public record MemoryFact(
    String id,
    String content,
    MemoryLayer layer,
    Instant createdAt
) {}
