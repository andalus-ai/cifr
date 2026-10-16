package tech.kayys.aljabr.engine.runtime.event;

/**
 * Payload recorded when an action execution transaction begins.
 */
public record ExecutionStartedPayload(
    String transactionId,
    String actionId
) implements EventPayload {
    @Override
    public String payloadType() {
        return "ExecutionStarted";
    }
}
