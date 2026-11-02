package tech.kayys.cifr.engine.memory;

import java.util.Optional;

public record MemoryCandidate(
    String content,
    String source,
    Optional<String> explicitlyStatedScope
) {}
