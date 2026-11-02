package tech.kayys.cifr.engine.runtime.state;

import tech.kayys.cifr.engine.lifecycle.AgentState;
import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskStatus;

/**
 * Materialized task state derived from events.
 */
public record TaskRuntimeState(
    TaskId taskId,
    TaskStatus status,
    AgentState lifecycleState,
    long lastSequence
) {
    public static TaskRuntimeState initial(TaskId id) {
        return new TaskRuntimeState(id, TaskStatus.RUNNING, AgentState.INTAKE, 0L);
    }
}
