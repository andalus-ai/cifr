package tech.kayys.cifr.api.agent;

public enum AgentPhase {
    INITIALIZING,
    OBSERVING,
    PLANNING,
    EXECUTING,
    VERIFYING,
    REVIEWING,
    REPLANNING,
    RECOVERING,
    WAITING_FOR_USER,
    EVALUATING,
    COMPLETED,
    FAILED,
    CANCELLED
}
