package tech.kayys.aljabr.engine.runtime.event;

import tech.kayys.aljabr.engine.planning.TaskStepId;

/**
 * Payload recorded when a task step completes successfully.
 */
public record StepCompletedPayload(
    TaskStepId stepId,
    String outcomeSummary
) implements EventPayload {
    @Override
    public String payloadType() {
        return "StepCompleted";
    }
}
