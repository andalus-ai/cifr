package tech.kayys.aljabr.engine.lifecycle;

import tech.kayys.aljabr.engine.task.TaskId;

import java.time.Instant;

/**
 * Record representing a single model invocation, ensuring inference is transactional and traceable.
 */
public record ModelInvocation(
    String invocationId,
    TaskId taskId,
    String modelName,
    String promptFingerprint,
    String status,
    Instant startedAt,
    Instant completedAt
) {
    public static ModelInvocation start(String invocationId, TaskId taskId, String modelName, String promptFingerprint) {
        return new ModelInvocation(invocationId, taskId, modelName, promptFingerprint, "STARTED", Instant.now(), null);
    }

    public ModelInvocation complete() {
        return new ModelInvocation(invocationId, taskId, modelName, promptFingerprint, "COMPLETED", startedAt, Instant.now());
    }

    public ModelInvocation failed(String reason) {
        return new ModelInvocation(invocationId, taskId, modelName, promptFingerprint, "FAILED: " + reason, startedAt, Instant.now());
    }
}
