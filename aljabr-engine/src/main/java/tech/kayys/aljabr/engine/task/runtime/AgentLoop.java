package tech.kayys.aljabr.engine.task.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.task.TaskId;
import tech.kayys.aljabr.engine.task.TaskResult;

public interface AgentLoop {
    Uni<TaskResult> run(TaskId taskId);
}
