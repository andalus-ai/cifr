package tech.kayys.aljabr.engine.policy;

import java.time.Instant;

/**
 * Structured request for user approval of a sensitive or irreversible action.
 */
public record ApprovalRequest(
    String id,
    String actionSummary,
    String reason,
    Instant expiresAt
) {
    public static ApprovalRequest of(String id, String actionSummary, String reason) {
        return new ApprovalRequest(id, actionSummary, reason, Instant.now().plusSeconds(300));
    }
}
