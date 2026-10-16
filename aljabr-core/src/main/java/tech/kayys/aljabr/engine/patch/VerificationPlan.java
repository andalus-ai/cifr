package tech.kayys.aljabr.engine.patch;

import java.util.List;
import tech.kayys.aljabr.engine.verification.VerificationLevel;

public record VerificationPlan(
    List<VerificationAction> actions,
    VerificationLevel level,
    String rationale
) {
    public VerificationPlan {
        actions = actions != null ? List.copyOf(actions) : List.of();
    }
}
