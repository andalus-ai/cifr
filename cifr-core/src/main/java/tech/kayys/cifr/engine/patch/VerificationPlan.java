package tech.kayys.cifr.engine.patch;

import java.util.List;
import tech.kayys.cifr.engine.verification.VerificationLevel;

public record VerificationPlan(
    List<VerificationAction> actions,
    VerificationLevel level,
    String rationale
) {
    public VerificationPlan {
        actions = actions != null ? List.copyOf(actions) : List.of();
    }
}
