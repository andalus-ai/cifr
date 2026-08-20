package tech.kayys.aljabr.engine.action;

/**
 * Status resulting from action execution.
 */
public enum ActionResultStatus {
    SUCCESS,
    PARTIAL,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    REJECTED
}
