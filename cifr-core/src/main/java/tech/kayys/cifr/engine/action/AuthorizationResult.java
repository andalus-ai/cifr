package tech.kayys.cifr.engine.action;

import tech.kayys.cifr.engine.policy.Decision;

import java.util.Optional;

/**
 * Result of an authorization check for an action proposal.
 */
public record AuthorizationResult(
    boolean isAuthorized,
    Optional<AuthorizedAction> action,
    Decision decision
) {
    public static AuthorizationResult granted(AuthorizedAction action, Decision decision) {
        return new AuthorizationResult(true, Optional.of(action), decision);
    }

    public static AuthorizationResult denied(Decision decision) {
        return new AuthorizationResult(false, Optional.empty(), decision);
    }
}
