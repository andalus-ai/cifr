package tech.kayys.aljabr.engine.orchestrator.output;

/**
 * Status of an orchestrated tool execution.
 */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILURE,
    TIMED_OUT,
    CANCELLED,
    REJECTED
}
