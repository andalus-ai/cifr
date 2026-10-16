package tech.kayys.aljabr.engine.store;

/**
 * Lifecycle states of an individual memory entry.
 */
public enum MemoryLifecycle {
    ACTIVE,
    STALE,
    SUPERSEDED,
    REVOKED,
    EXPIRED,
    FORGOTTEN
}
