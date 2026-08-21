package tech.kayys.aljabr.engine.capability;

/**
 * Execution idempotency guarantees for capabilities.
 */
public enum Idempotency {
    SAFE,
    CONDITIONALLY_SAFE,
    UNSAFE
}
