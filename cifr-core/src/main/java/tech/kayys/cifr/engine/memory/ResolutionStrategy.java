package tech.kayys.cifr.engine.memory;

/**
 * Epistemic resolution strategy when two contradictory memory items are identified.
 */
public enum ResolutionStrategy {
    /** Keep the existing memory; discard the incoming candidate. */
    KEEP_EXISTING,

    /** Replace the existing memory with the incoming candidate. */
    KEEP_INCOMING,

    /** Keep both items with DISPUTED epistemic status for subsequent verification. */
    KEEP_BOTH,

    /** Supersede the existing memory with the newer incoming evidence. */
    SUPERSEDE_EXISTING,

    /** Defer resolution until explicit test or compiler verification passes. */
    REQUIRE_VERIFICATION
}
