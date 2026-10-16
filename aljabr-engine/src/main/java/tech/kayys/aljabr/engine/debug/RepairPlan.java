package tech.kayys.aljabr.engine.debug;

import java.util.List;
import tech.kayys.aljabr.engine.patch.ChangePlan;

public record RepairPlan(
    HypothesisId confirmedHypothesis,
    ChangePlan changePlan,
    List<String> rationale
) {
    public RepairPlan {
        rationale = rationale != null ? List.copyOf(rationale) : List.of();
    }
}
