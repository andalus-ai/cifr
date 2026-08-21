package tech.kayys.aljabr.engine.runtime.event;

import tech.kayys.aljabr.engine.action.ActionType;
import tech.kayys.aljabr.engine.action.ActionTarget;

/**
 * Payload recorded when a model proposes an action.
 */
public record ActionProposedPayload(
    String proposalId,
    ActionType actionType,
    ActionTarget target,
    String reason,
    double confidence
) implements EventPayload {
    @Override
    public String payloadType() {
        return "ActionProposed";
    }
}
