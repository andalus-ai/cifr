package tech.kayys.aljabr.engine.runtime.event;

import tech.kayys.aljabr.engine.contract.GoalContract;

/**
 * Payload recorded when a new agent task is initialized.
 */
public record TaskCreatedPayload(
    String workspaceId,
    GoalContract goalContract
) implements EventPayload {
    @Override
    public String payloadType() {
        return "TaskCreated";
    }
}
