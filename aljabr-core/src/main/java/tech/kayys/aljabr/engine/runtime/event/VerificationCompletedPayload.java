package tech.kayys.aljabr.engine.runtime.event;

import tech.kayys.aljabr.engine.verification.VerificationLevel;

/**
 * Payload recorded when a verification stage finishes evaluation.
 */
public record VerificationCompletedPayload(
    VerificationLevel level,
    boolean passed,
    String summary
) implements EventPayload {
    @Override
    public String payloadType() {
        return "VerificationCompleted";
    }
}
