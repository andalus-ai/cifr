package tech.kayys.cifr.engine.exec.intelligence;

public enum FailureType {
    COMPILATION,
    TEST_ASSERTION,
    TEST_ERROR,
    TIMEOUT,
    DEPENDENCY,
    CONFIGURATION,
    ENVIRONMENT,
    RESOURCE,
    NETWORK,
    TOOLING,
    UNKNOWN
}
