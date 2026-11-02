package tech.kayys.cifr.engine.gate;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.loop.CodingTask;
import tech.kayys.cifr.engine.phase.TaskExecutionResult;

public interface CompletionGate {
    Uni<CompletionDecision> evaluate(CodingTask task, TaskExecutionResult result, CodingExecutionContext context);
}
