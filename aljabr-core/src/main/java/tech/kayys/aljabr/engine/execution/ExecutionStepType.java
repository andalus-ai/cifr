package tech.kayys.aljabr.engine.execution;

/**
 * Functional step category executed by the state machine.
 */
public enum ExecutionStepType {
    ANALYSIS,
    CONTEXT_BUILD,
    MODEL_DECISION,
    TOOL_EXECUTION,
    OBSERVATION,
    VERIFICATION,
    REPLAN,
    RESPONSE
}
