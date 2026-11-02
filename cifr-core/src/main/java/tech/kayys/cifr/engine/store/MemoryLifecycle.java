package tech.kayys.cifr.engine.store;

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
