package tech.kayys.aljabr.engine.task;

import java.time.Instant;

public record TaskResult(
    TaskId taskId,
    boolean success,
    String outcome,
    Instant completedAt
) {
    public static TaskResult success(TaskId id, String outcome) {
        return new TaskResult(id, true, outcome, Instant.now());
    }

    public static TaskResult failure(TaskId id, String reason) {
        return new TaskResult(id, false, reason, Instant.now());
    }
}
