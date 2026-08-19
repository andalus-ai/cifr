package tech.kayys.aljabr.engine.exec.intelligence;

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
