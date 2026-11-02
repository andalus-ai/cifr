package tech.kayys.cifr.engine.patch;

import java.util.List;

public record ChangePlan(
    ChangePlanId id,
    String objective,
    List<PlannedChange> changes,
    List<String> evidenceIds,
    ChangeRisk risk
) {
    public ChangePlan {
        changes = changes != null ? List.copyOf(changes) : List.of();
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }
}
