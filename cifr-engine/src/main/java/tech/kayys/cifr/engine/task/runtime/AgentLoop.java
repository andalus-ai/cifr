package tech.kayys.cifr.engine.task.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskResult;

public interface AgentLoop {
    Uni<TaskResult> run(TaskId taskId);
}
