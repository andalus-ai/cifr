package tech.kayys.cifr.engine.orchestrator.exec;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.orchestrator.ResolvedTool;
import tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget;
import tech.kayys.cifr.engine.exec.ExecutionResult;

public interface ExecutionEngine {

    /** Legacy contract used by existing adapters and DefaultExecutionEngine. */
    Uni<ExecutionResult> execute(String executionId, ResolvedTool tool, ExecutionBudget budget);

    /** Convenience async handle wrapper — delegates to execute(). */
    default Uni<ExecutionHandle> start(ResolvedTool tool, ExecutionBudget budget) {
        String id = "exec-" + System.currentTimeMillis();
        return execute(id, tool, budget)
            .map(result -> new ExecutionHandle() {
                @Override
                public Uni<ExecutionResult> await() {
                    return Uni.createFrom().item(result);
                }

                @Override
                public Uni<Void> cancel() {
                    return Uni.createFrom().voidItem();
                }
            });
    }
}
