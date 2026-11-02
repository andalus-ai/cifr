package tech.kayys.cifr.engine.planning;

import tech.kayys.cifr.engine.task.step.StepStatus;
import tech.kayys.cifr.engine.task.step.StepType;

import java.util.Set;

/**
 * An individual step inside a TaskGraph.
 */
public record TaskStep(
    TaskStepId id,
    String objective,
    StepType type,
    StepStatus status,
    Set<TaskStepId> dependencies,
    Set<String> evidenceIds
) {
    public TaskStep {
        dependencies = dependencies != null ? Set.copyOf(dependencies) : Set.of();
        evidenceIds = evidenceIds != null ? Set.copyOf(evidenceIds) : Set.of();
    }

    public static TaskStep of(TaskStepId id, String objective, StepType type) {
        return new TaskStep(id, objective, type, StepStatus.PENDING, Set.of(), Set.of());
    }

    public TaskStep withStatus(StepStatus nextStatus) {
        return new TaskStep(id, objective, type, nextStatus, dependencies, evidenceIds);
    }
}
