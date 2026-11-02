package tech.kayys.cifr.engine.execution;

/**
 * Evaluated termination decision for an execution loop.
 */
public enum TerminationDecision {
    CONTINUE,
    COMPLETE,
    FAIL,
    ESCALATE,
    PAUSE
}
