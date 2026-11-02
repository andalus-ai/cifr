package tech.kayys.cifr.engine.runtime.event;

import tech.kayys.cifr.engine.verification.VerificationLevel;

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
