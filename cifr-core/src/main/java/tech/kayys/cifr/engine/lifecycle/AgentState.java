package tech.kayys.cifr.engine.lifecycle;

/**
 * Fundamental runtime states of the agent task lifecycle.
 * The state machine owns the lifecycle; the model does not.
 */
public enum AgentState {
    INTAKE,
    CLASSIFY,
    UNDERSTAND,
    RETRIEVE,
    PLAN,
    EXECUTE,
    OBSERVE,
    VERIFY,
    REPAIR,
    COMPLETE,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETE || this == FAILED || this == CANCELLED;
    }
}
