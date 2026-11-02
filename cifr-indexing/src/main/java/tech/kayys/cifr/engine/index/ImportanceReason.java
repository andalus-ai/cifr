package tech.kayys.cifr.engine.index;

/**
 * Why a file is considered important for the current task.
 */
public enum ImportanceReason {
    DIRECTLY_REFERENCED_BY_TASK,
    EXACT_SYMBOL_MATCH,
    DEPENDENCY_PROXIMITY,
    RECENTLY_MODIFIED,
    TEST_ASSOCIATED,
    PUBLIC_API,
    CONFIGURATION,
    DOCUMENTATION,
    GENERATED
}
