package tech.kayys.aljabr.engine.policy;

/**
 * Fundamental policy decision types.
 */
public enum DecisionType {
    ALLOW,
    DENY,
    ALLOW_WITH_LIMIT,
    REQUIRE_APPROVAL,
    DEFER,
    FALLBACK
}
