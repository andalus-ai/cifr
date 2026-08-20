package tech.kayys.aljabr.engine.plan;

import java.util.List;
import tech.kayys.aljabr.engine.exec.ExecutionEvidence;

public record PlanExecution(
    AgentPlan plan,
    List<ExecutionEvidence> executionHistory,
    boolean isSuccessful
) {
    public PlanExecution {
        executionHistory = executionHistory != null ? List.copyOf(executionHistory) : List.of();
    }
}
