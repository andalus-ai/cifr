package tech.kayys.cifr.engine.memory;

import java.util.Optional;

public interface MemoryLifecycle {
    void persist(MemoryCandidate candidate, MemoryPolicy policy);
    void archive(String id);
    void delete(String id);
    Optional<MemoryFact> getFactById(String id);
}
