package tech.kayys.aljabr.engine.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.store.Memory;

/**
 * SPI for resolving contradictions and epistemic conflicts between memories.
 */
public interface MemoryConflictResolver {

    /**
     * Resolves an epistemic conflict between an existing stored memory and an incoming candidate.
     */
    Uni<ConflictResolution> resolve(Memory existing, Memory incoming);
}
