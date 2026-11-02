package tech.kayys.cifr.engine.runtime.event;

import tech.kayys.cifr.engine.contract.GoalContract;

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
