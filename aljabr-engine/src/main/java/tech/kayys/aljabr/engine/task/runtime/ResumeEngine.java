package tech.kayys.aljabr.engine.task.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.task.TaskId;
import tech.kayys.aljabr.engine.task.TaskState;

public interface ResumeEngine {
    Uni<TaskState> resume(TaskId taskId);
    Uni<Boolean> canResume(TaskId taskId);
}
