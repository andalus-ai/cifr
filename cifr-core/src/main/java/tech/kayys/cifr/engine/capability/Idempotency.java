package tech.kayys.cifr.engine.capability;

/**
 * Execution idempotency guarantees for capabilities.
 */
public enum Idempotency {
    SAFE,
    CONDITIONALLY_SAFE,
    UNSAFE
}
