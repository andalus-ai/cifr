package tech.kayys.aljabr.engine.task.event;

import tech.kayys.aljabr.engine.task.TaskId;

import java.time.Instant;
import java.util.Map;

public record TaskEvent(
    String eventId,
    TaskId taskId,
    TaskEventType type,
    Map<String, Object> payload,
    Instant occurredAt
) {
    public TaskEvent {
        payload = payload != null ? Map.copyOf(payload) : Map.of();
    }

    public static TaskEvent of(TaskId taskId, TaskEventType type, Map<String, Object> payload) {
        return new TaskEvent("evt-" + java.util.UUID.randomUUID(), taskId, type, payload, Instant.now());
    }

    public static TaskEvent of(TaskId taskId, TaskEventType type) {
        return of(taskId, type, Map.of());
    }
}
