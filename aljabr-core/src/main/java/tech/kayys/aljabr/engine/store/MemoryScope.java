package tech.kayys.aljabr.engine.store;

/**
 * Scopes governing the visibility, lifetime, and access boundaries of retained memories.
 */
public enum MemoryScope {
    INVOCATION,
    TASK,
    SESSION,
    WORKSPACE,
    PROJECT,
    USER,
    GLOBAL
}
