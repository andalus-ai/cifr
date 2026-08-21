package tech.kayys.aljabr.engine.runtime.state;

import tech.kayys.aljabr.engine.planning.TaskStepId;
import tech.kayys.aljabr.engine.task.step.StepStatus;

/**
 * Materialized step state derived from events.
 */
public record StepRuntimeState(
    TaskStepId stepId,
    StepStatus status,
    String objective
) {
    public static StepRuntimeState of(TaskStepId id, String objective) {
        return new StepRuntimeState(id, StepStatus.PENDING, objective);
    }
}
