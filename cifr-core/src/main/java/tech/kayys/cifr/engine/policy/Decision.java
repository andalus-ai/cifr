package tech.kayys.cifr.engine.policy;

import java.util.Optional;

/**
 * Structured decision returned by any policy evaluation.
 */
public record Decision(
    DecisionType type,
    DecisionReason reason,
    Optional<String> ruleId,
    Optional<String> approvalId
) {
    public boolean isAllowed() {
        return type == DecisionType.ALLOW || type == DecisionType.ALLOW_WITH_LIMIT;
    }

    public static Decision allow(String reason) {
        return new Decision(DecisionType.ALLOW, DecisionReason.of("ALLOW", reason), Optional.empty(), Optional.empty());
    }

    public static Decision deny(String ruleId, String reason) {
        return new Decision(DecisionType.DENY, DecisionReason.of("DENY", reason), Optional.of(ruleId), Optional.empty());
    }

    public static Decision requireApproval(String approvalId, String reason) {
        return new Decision(DecisionType.REQUIRE_APPROVAL, DecisionReason.of("APPROVAL_REQUIRED", reason), Optional.empty(), Optional.of(approvalId));
    }
}
