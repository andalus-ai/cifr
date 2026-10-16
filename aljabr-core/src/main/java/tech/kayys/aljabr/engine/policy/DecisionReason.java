package tech.kayys.aljabr.engine.policy;

/**
 * Structured reason explaining why a policy decision was reached.
 */
public record DecisionReason(
    String code,
    String explanation
) {
    public static DecisionReason of(String code, String explanation) {
        return new DecisionReason(code, explanation);
    }
}
