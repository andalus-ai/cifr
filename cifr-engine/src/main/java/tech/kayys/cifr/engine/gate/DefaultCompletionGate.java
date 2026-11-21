package tech.kayys.cifr.engine.gate;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.loop.CodingTask;
import tech.kayys.cifr.engine.phase.TaskExecutionResult;

/**
 * Conservative default completion gate.
 *
 * <p>A completion claim is not evidence that a task has been completed. Until a
 * configured evidence-backed gate is available, the default implementation
 * deliberately rejects completion.</p>
 */
@ApplicationScoped
public class DefaultCompletionGate implements CompletionGate {

    @Override
    public Uni<CompletionDecision> evaluate(
            CodingTask task,
            TaskExecutionResult result,
            CodingExecutionContext context) {
        if (task == null || result == null || context == null) {
            return Uni.createFrom().item(new CompletionDecision(false,
                    "Completion cannot be evaluated without task, result and execution context."));
        }

        return Uni.createFrom().item(new CompletionDecision(false,
                "Completion is unverified: no evidence-backed completion policy is configured."));
    }
}
