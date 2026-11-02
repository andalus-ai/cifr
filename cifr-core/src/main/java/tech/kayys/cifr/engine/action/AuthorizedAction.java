package tech.kayys.cifr.engine.action;

import java.time.Instant;

/**
 * An action proposal that has passed policy evaluation and is authorized for execution.
 */
public record AuthorizedAction(
    String actionId,
    ActionProposal proposal,
    Instant authorizedAt,
    Instant expiresAt
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public static AuthorizedAction of(String actionId, ActionProposal proposal) {
        Instant now = Instant.now();
        return new AuthorizedAction(actionId, proposal, now, now.plusSeconds(300));
    }
}
