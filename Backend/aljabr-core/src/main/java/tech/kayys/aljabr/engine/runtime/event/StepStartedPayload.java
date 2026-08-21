package tech.kayys.aljabr.engine.runtime.event;

import tech.kayys.aljabr.engine.planning.TaskStepId;

/**
 * Payload recorded when a task step begins execution.
 */
public record StepStartedPayload(
    TaskStepId stepId,
    String objective
) implements EventPayload {
    @Override
    public String payloadType() {
        return "StepStarted";
    }
}
