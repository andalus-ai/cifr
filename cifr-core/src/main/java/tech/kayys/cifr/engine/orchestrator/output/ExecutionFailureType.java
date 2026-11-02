package tech.kayys.cifr.engine.orchestrator.output;

public enum ExecutionFailureType {
    CODE_FAILURE,
    BUILD_FAILURE,
    TEST_FAILURE,
    ENVIRONMENT_FAILURE,
    TOOL_FAILURE,
    NETWORK_FAILURE,
    RESOURCE_FAILURE,
    TIMEOUT,
    PERMISSION_FAILURE
}
