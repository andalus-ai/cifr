package tech.kayys.aljabr.engine.execution;

/**
 * Reason triggering replanning within the execution cycle.
 */
public enum ReplanReason {
    NEW_EVIDENCE,
    ACTION_FAILURE,
    VERIFICATION_FAILURE,
    GOAL_CHANGE,
    USER_INTERVENTION,
    CAPABILITY_CHANGE,
    BUDGET_CONSTRAINT,
    CONTEXT_CHANGE
}
