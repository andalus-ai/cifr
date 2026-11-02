package tech.kayys.cifr.engine.debug;

import java.util.List;
import tech.kayys.cifr.engine.patch.ChangePlan;

public record RepairPlan(
    HypothesisId confirmedHypothesis,
    ChangePlan changePlan,
    List<String> rationale
) {
    public RepairPlan {
        rationale = rationale != null ? List.copyOf(rationale) : List.of();
    }
}
