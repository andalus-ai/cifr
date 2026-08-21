package tech.kayys.aljabr.engine.execution;

/**
 * Explicit 15-state lifecycle for durable agent execution.
 */
public enum ExecutionState {
    CREATED,
    ANALYZING,
    BUILDING_CONTEXT,
    THINKING,
    AWAITING_ACTION,
    VALIDATING_ACTION,
    AUTHORIZING_ACTION,
    EXECUTING,
    OBSERVING,
    VERIFYING,
    REPLANNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isRunning() {
        return !isTerminal() && this != PAUSED;
    }
}
