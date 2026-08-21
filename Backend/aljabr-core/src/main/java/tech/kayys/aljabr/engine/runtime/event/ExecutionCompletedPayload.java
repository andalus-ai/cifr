package tech.kayys.aljabr.engine.runtime.event;

import tech.kayys.aljabr.engine.action.ActionResultStatus;

/**
 * Payload recorded when an action execution transaction completes.
 */
public record ExecutionCompletedPayload(
    String transactionId,
    ActionResultStatus status,
    String summary
) implements EventPayload {
    @Override
    public String payloadType() {
        return "ExecutionCompleted";
    }
}
