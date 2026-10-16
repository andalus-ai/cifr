package tech.kayys.aljabr.engine.lifecycle;

/**
 * Consistency status of workspace after a process restart or crash.
 */
public enum WorkspaceConsistency {
    UNCHANGED,
    FULLY_APPLIED,
    PARTIALLY_APPLIED,
    EXTERNALLY_CHANGED,
    UNKNOWN
}
