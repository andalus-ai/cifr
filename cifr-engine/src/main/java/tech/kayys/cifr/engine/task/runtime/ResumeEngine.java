package tech.kayys.cifr.engine.task.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskState;

public interface ResumeEngine {
    Uni<TaskState> resume(TaskId taskId);
    Uni<Boolean> canResume(TaskId taskId);
}
