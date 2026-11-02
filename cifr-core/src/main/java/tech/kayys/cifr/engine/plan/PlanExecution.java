package tech.kayys.cifr.engine.plan;

import java.util.List;
import tech.kayys.cifr.engine.exec.ExecutionEvidence;

public record PlanExecution(
    AgentPlan plan,
    List<ExecutionEvidence> executionHistory,
    boolean isSuccessful
) {
    public PlanExecution {
        executionHistory = executionHistory != null ? List.copyOf(executionHistory) : List.of();
    }
}
