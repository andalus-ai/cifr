package tech.kayys.cifr.engine.runtime.state;

import tech.kayys.cifr.engine.planning.TaskStepId;
import tech.kayys.cifr.engine.task.step.StepStatus;

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
