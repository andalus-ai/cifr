package tech.kayys.aljabr.engine.memory;

import io.smallrye.mutiny.Uni;

/**
 * Service for executing intentional forgetting, redaction, and cascade invalidation.
 */
public interface MemoryForgetter {

    /**
     * Executes the requested forgetting operation and cascades invalidation to derived memories.
     */
    Uni<ForgetResult> forget(ForgetRequest request);
}
