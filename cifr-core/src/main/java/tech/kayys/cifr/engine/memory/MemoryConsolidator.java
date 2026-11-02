package tech.kayys.cifr.engine.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.store.MemoryScope;

/**
 * Background and post-task memory consolidation pipeline.
 * Evaluates working and episodic memories, promoting verified invariants to semantic/procedural memory.
 */
public interface MemoryConsolidator {

    /**
     * Consolidates memories for the specified scope ID and level.
     */
    Uni<ConsolidationReport> consolidate(String scopeId, MemoryScope scope);
}
