package tech.kayys.aljabr.engine.plan;

import java.util.List;
import java.util.Optional;

public record AgentPlan(
    String id,
    String goal,
    List<PlanStep> steps,
    PlanState state,
    int replanCount
) {
    public AgentPlan {
        steps = steps != null ? List.copyOf(steps) : List.of();
    }

    public Optional<PlanStep> nextPendingStep() {
        return steps.stream()
            .filter(s -> s.status() == PlanStepStatus.PENDING)
            .findFirst();
    }
}
