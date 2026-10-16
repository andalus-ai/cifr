package tech.kayys.aljabr.engine.gate;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.loop.CodingTask;
import tech.kayys.aljabr.engine.phase.TaskExecutionResult;

public interface CompletionGate {
    Uni<CompletionDecision> evaluate(CodingTask task, TaskExecutionResult result, CodingExecutionContext context);
}
