package tech.kayys.cifr.engine.runtime.event;

import tech.kayys.cifr.engine.task.TaskId;

import java.time.Instant;

/**
 * Immutable envelope for all domain and runtime state events.
 */
public record RuntimeEvent(
    EventId id,
    EventType type,
    TaskId taskId,
    Instant timestamp,
    long sequence,
    EventPayload payload,
    EventMetadata metadata
) {
    public static RuntimeEvent create(TaskId taskId, long sequence, EventType type, EventPayload payload) {
        return new RuntimeEvent(
            EventId.generate(),
            type,
            taskId,
            Instant.now(),
            sequence,
            payload,
            EventMetadata.create(taskId.value(), null)
        );
    }
}
