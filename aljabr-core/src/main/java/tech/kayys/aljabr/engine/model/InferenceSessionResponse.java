package tech.kayys.aljabr.engine.model;

import java.time.Duration;

/**
 * Result of a model inference session including telemetry and escalation metadata.
 */
public record InferenceSessionResponse(
    String requestId,
    String content,
    String tierUsed,
    Duration duration,
    double tokensPerSecond,
    boolean escalated,
    EscalationReason escalationReason
) {
    public static InferenceSessionResponse success(String reqId, String content, String tier, Duration duration, double tokPerSec) {
        return new InferenceSessionResponse(reqId, content, tier, duration, tokPerSec, false, EscalationReason.NONE);
    }

    public static InferenceSessionResponse escalated(String reqId, String content, String tier, Duration duration, double tokPerSec, EscalationReason reason) {
        return new InferenceSessionResponse(reqId, content, tier, duration, tokPerSec, true, reason);
    }
}
