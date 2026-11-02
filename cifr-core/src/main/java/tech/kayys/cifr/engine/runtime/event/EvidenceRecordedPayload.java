package tech.kayys.cifr.engine.runtime.event;

import tech.kayys.cifr.engine.store.EvidenceType;

/**
 * Payload recorded when an immutable evidence observation is captured.
 */
public record EvidenceRecordedPayload(
    String evidenceId,
    EvidenceType type,
    String summary
) implements EventPayload {
    @Override
    public String payloadType() {
        return "EvidenceRecorded";
    }
}
