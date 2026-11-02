package tech.kayys.cifr.engine.lifecycle;

/**
 * Outcome reported by an AgentStateHandler after executing work within a state.
 */
public enum StateOutcome {
    SUCCESS,
    NEEDS_RETRIEVAL,
    NEEDS_PLANNING,
    NEEDS_EXECUTION,
    NEEDS_VERIFICATION,
    RETRYABLE_FAILURE,
    TERMINAL_FAILURE,
    CANCELLED
}
