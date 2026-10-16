package tech.kayys.aljabr.engine.runtime.state;

import tech.kayys.aljabr.engine.planning.TaskStepId;
import tech.kayys.aljabr.engine.task.TaskId;

import java.util.Map;
import java.util.Set;

/**
 * Complete, immutable materialized runtime state reconstructed purely from events.
 */
public record RuntimeState(
    TaskRuntimeState task,
    PlanRuntimeState plan,
    Map<TaskStepId, StepRuntimeState> steps,
    Map<String, ActionRuntimeState> actions,
    Map<String, ExecutionRuntimeState> executions,
    Set<String> evidenceIds
) {
    public RuntimeState {
        steps = steps != null ? Map.copyOf(steps) : Map.of();
        actions = actions != null ? Map.copyOf(actions) : Map.of();
        executions = executions != null ? Map.copyOf(executions) : Map.of();
        evidenceIds = evidenceIds != null ? Set.copyOf(evidenceIds) : Set.of();
    }

    public static RuntimeState empty(TaskId taskId) {
        return new RuntimeState(
            TaskRuntimeState.initial(taskId),
            PlanRuntimeState.empty(),
            Map.of(),
            Map.of(),
            Map.of(),
            Set.of()
        );
    }
}
