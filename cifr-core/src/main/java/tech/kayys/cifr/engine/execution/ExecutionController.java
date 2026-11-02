package tech.kayys.cifr.engine.execution;

import io.smallrye.mutiny.Uni;

/**
 * Control operations for pausing, resuming, cancelling, and authorizing executions.
 */
public interface ExecutionController {
    Uni<Void> pause(ExecutionId id);
    Uni<Void> resume(ExecutionId id);
    Uni<Void> cancel(ExecutionId id, String reason);
    Uni<Void> approve(ExecutionId id, String actionId);
    Uni<Void> deny(ExecutionId id, String actionId, String reason);
}
