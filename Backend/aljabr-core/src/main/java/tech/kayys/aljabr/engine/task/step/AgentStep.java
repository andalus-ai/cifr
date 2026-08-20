package tech.kayys.aljabr.engine.task.step;

import tech.kayys.aljabr.engine.task.TaskId;

public record AgentStep(StepId id, TaskId taskId, StepType type, StepInput input) {
    public static AgentStep of(TaskId taskId, StepType type) {
        return new AgentStep(StepId.generate(), taskId, type, StepInput.empty());
    }
}
