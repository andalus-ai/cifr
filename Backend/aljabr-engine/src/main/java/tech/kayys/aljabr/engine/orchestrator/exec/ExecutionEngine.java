package tech.kayys.aljabr.engine.orchestrator.exec;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionBudget;

public interface ExecutionEngine {

    Uni<ExecutionResult> execute(String executionId, ResolvedTool tool, ExecutionBudget budget);
}
