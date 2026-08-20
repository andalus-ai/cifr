package tech.kayys.aljabr.engine.lifecycle;

/**
 * Recovery decisions returned by the RecoveryPolicy upon encountering a failure.
 */
public enum RecoveryDecision {
    RETRY,
    REPAIR,
    REPLAN,
    FALLBACK_MODEL,
    WAIT,
    ABORT,
    ASK_USER
}
