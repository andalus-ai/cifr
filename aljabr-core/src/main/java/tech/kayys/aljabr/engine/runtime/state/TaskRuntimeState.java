package tech.kayys.aljabr.engine.runtime.state;

import tech.kayys.aljabr.engine.lifecycle.AgentState;
import tech.kayys.aljabr.engine.task.TaskId;
import tech.kayys.aljabr.engine.task.TaskStatus;

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
