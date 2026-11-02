package tech.kayys.cifr.engine.runtime.event;

import java.time.Instant;

/**
 * Payload recorded when policy authorizes an action.
 */
public record ActionAuthorizedPayload(
    String actionId,
    String proposalId,
    Instant expiresAt
) implements EventPayload {
    @Override
    public String payloadType() {
        return "ActionAuthorized";
    }
}
