package tech.kayys.aljabr.engine.runtime.event;

import tech.kayys.aljabr.engine.store.EvidenceType;

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
