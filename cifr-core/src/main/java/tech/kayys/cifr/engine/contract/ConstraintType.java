package tech.kayys.cifr.engine.contract;

/**
 * Categories of constraints placed on execution.
 */
public enum ConstraintType {
    DO_NOT_MODIFY_PUBLIC_API,
    DO_NOT_MODIFY_SCHEMA,
    DO_NOT_DELETE_FILES,
    TOKEN_BUDGET_LIMIT,
    TIME_LIMIT,
    CUSTOM
}
