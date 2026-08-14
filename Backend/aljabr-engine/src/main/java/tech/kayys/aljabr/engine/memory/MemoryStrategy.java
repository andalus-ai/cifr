package tech.kayys.aljabr.engine.memory;

import java.util.Optional;

public interface MemoryStrategy {
    Optional<MemoryPolicy> evaluate(MemoryCandidate candidate);
}
