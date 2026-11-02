package tech.kayys.cifr.engine.runtime.event;

/**
 * Payload recorded when a task reaches terminal completion.
 */
public record TaskCompletedPayload(
    boolean success,
    String summary
) implements EventPayload {
    @Override
    public String payloadType() {
        return "TaskCompleted";
    }
}
