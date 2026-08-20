package tech.kayys.aljabr.engine.store;

import tech.kayys.aljabr.engine.task.TaskId;

import java.util.List;

/**
 * Snapshot of model context assembled for a specific reasoning invocation.
 */
public record ContextSnapshot(
    String contextId,
    TaskId taskId,
    List<String> items,
    int tokenEstimate,
    String fingerprint
) {
    public ContextSnapshot {
        items = items != null ? List.copyOf(items) : List.of();
    }
}
