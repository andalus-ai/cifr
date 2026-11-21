package tech.kayys.cifr.engine.verification.graph;

/**
 * Outcome of an actual verification check. NOT_RUN and SKIPPED are never success.
 */
public enum ExecutionCheckStatus {
    PASSED,
    FAILED,
    NOT_RUN,
    ERROR,
    SKIPPED
}
