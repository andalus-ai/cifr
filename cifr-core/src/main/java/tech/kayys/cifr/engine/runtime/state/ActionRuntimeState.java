package tech.kayys.cifr.engine.runtime.state;

import tech.kayys.cifr.engine.action.ActionTarget;
import tech.kayys.cifr.engine.action.ActionType;

import java.time.Instant;
import java.util.Optional;

/**
 * Materialized action proposal and authorization state.
 */
public record ActionRuntimeState(
    String proposalId,
    ActionType actionType,
    ActionTarget target,
    boolean isAuthorized,
    Optional<String> authorizedActionId,
    Optional<Instant> expiresAt
) {
    public static ActionRuntimeState proposed(String proposalId, ActionType type, ActionTarget target) {
        return new ActionRuntimeState(proposalId, type, target, false, Optional.empty(), Optional.empty());
    }

    public ActionRuntimeState authorize(String actionId, Instant expiresAt) {
        return new ActionRuntimeState(proposalId, actionType, target, true, Optional.of(actionId), Optional.ofNullable(expiresAt));
    }
}
