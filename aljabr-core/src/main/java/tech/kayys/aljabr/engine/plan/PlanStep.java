package tech.kayys.aljabr.engine.plan;

import java.util.List;
import java.util.Set;

public record PlanStep(
    String id,
    String objective,
    PlanStepType type,
    Set<String> dependencies,
    PlanStepStatus status,
    List<String> evidenceIds
) {
    public PlanStep(String id, String objective) {
        this(id, objective, PlanStepType.OBSERVE, Set.of(), PlanStepStatus.PENDING, List.of());
    }

    public PlanStep {
        dependencies = dependencies != null ? Set.copyOf(dependencies) : Set.of();
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }
}
