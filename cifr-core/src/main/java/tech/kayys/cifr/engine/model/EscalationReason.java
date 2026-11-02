package tech.kayys.cifr.engine.model;

/**
 * Documented reason for escalating an inference task from a local tier to a higher tier.
 */
public enum EscalationReason {
    NONE,
    CAPABILITY_MISMATCH,
    CONTEXT_LIMIT_EXCEEDED,
    OUTPUT_SCHEMA_INVALID,
    EXECUTION_FAILURE,
    VERIFICATION_FAILURE,
    CIRCUIT_BREAKER_OPEN,
    TIMEOUT,
    PROVIDER_UNAVAILABLE
}
