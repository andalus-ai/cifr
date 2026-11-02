package tech.kayys.cifr.engine.planning;

/**
 * Directed dependency between two steps in a TaskGraph.
 */
public record TaskDependency(
    TaskStepId fromStep,
    TaskStepId toStep,
    DependencyCondition condition
) {
    public static TaskDependency of(TaskStepId from, TaskStepId to) {
        return new TaskDependency(from, to, DependencyCondition.SUCCESS);
    }
}
