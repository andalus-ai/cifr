package tech.kayys.aljabr.engine.orchestrator.output;

public enum ExecutionStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED,
    CRASHED,
    BLOCKED,
    RESOURCE_LIMIT,
    UNKNOWN
}
