package tech.kayys.cifr.engine.store;

/**
 * Categories of immutable observations captured during execution.
 */
public enum EvidenceType {
    USER_INPUT,
    MODEL_OUTPUT,
    FILE_CONTENT,
    SEARCH_RESULT,
    TOOL_RESULT,
    BUILD_RESULT,
    TEST_RESULT,
    STATIC_ANALYSIS,
    PATCH_PROPOSAL,
    PATCH_APPLIED,
    WORKSPACE_STATE,
    ENVIRONMENT_STATE,
    ERROR
}
