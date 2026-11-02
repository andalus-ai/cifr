package tech.kayys.cifr.engine.phase;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.loop.CodingTask;

public interface TaskAgent {
    Uni<TaskExecutionResult> execute(CodingTask task, TaskExecutionContext context);
}
