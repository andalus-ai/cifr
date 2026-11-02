package tech.kayys.cifr.engine.store;

import tech.kayys.cifr.engine.task.TaskId;

import java.time.Instant;

/**
 * Immutable record of an observation made during execution.
 */
public record Evidence(
    String id,
    EvidenceType type,
    TaskId taskId,
    Instant timestamp,
    Provenance provenance,
    EvidencePayload payload
) {
    public static Evidence create(String id, EvidenceType type, TaskId taskId, Provenance provenance, EvidencePayload payload) {
        return new Evidence(id, type, taskId, Instant.now(), provenance, payload);
    }
}
