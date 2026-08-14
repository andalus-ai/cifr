package tech.kayys.aljabr.engine.phase;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.loop.CodingTask;

public interface TaskAgent {
    Uni<TaskExecutionResult> execute(CodingTask task, TaskExecutionContext context);
}
