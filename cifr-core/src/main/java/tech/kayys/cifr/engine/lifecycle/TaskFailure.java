package tech.kayys.cifr.engine.lifecycle;

/**
 * Categories of task-level failures to distinguish recoverable from terminal issues.
 */
public enum TaskFailure {
    INVALID_REQUEST,
    CAPABILITY_MISSING,
    POLICY_DENIED,
    MODEL_FAILURE,
    TOOL_FAILURE,
    VERIFICATION_FAILURE,
    RESOURCE_EXHAUSTED,
    CONFLICT,
    INTERNAL_ERROR
}
