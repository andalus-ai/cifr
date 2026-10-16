package tech.kayys.aljabr.engine.patch;

import java.util.List;

public record ChangeRisk(
    RiskLevel level,
    List<String> reasons
) {
    public ChangeRisk {
        if (level == null) {
            throw new IllegalArgumentException("risk level must not be null");
        }
        reasons = reasons != null ? List.copyOf(reasons) : List.of();
    }
}
