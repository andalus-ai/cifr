package tech.kayys.cifr.engine.lifecycle;

import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskStatus;
import tech.kayys.cifr.engine.task.TaskGoal;
import tech.kayys.cifr.engine.task.TaskProgress;

import java.time.Instant;

/**
 * Top-level persistent agent task object representing the entire unit of work.
 */
public record AgentTask(
    TaskId id,
    TaskStatus status,
    AgentState state,
    String workspaceId,
    TaskGoal goal,
    TaskProgress progress,
    Instant createdAt,
    Instant updatedAt
) {
    public static AgentTask create(TaskId id, String workspaceId, TaskGoal goal) {
        Instant now = Instant.now();
        return new AgentTask(id, TaskStatus.RUNNING, AgentState.INTAKE, workspaceId, goal, TaskProgress.initial(), now, now);
    }

    public AgentTask transitionTo(AgentState nextState) {
        return new AgentTask(id, status, nextState, workspaceId, goal, progress, createdAt, Instant.now());
    }

    public AgentTask withStatus(TaskStatus nextStatus) {
        return new AgentTask(id, nextStatus, state, workspaceId, goal, progress, createdAt, Instant.now());
    }
}
