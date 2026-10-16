package tech.kayys.aljabr.engine.memory;

/**
 * Epistemic validity state of a retained memory item.
 */
public enum MemoryValidity {
    UNKNOWN,
    VALID,
    STALE,
    CONTRADICTED,
    REVOKED
}
