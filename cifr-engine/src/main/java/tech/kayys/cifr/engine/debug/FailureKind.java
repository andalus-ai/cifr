package tech.kayys.cifr.engine.debug;

public enum FailureKind {
    COMPILATION,
    TEST_ASSERTION,
    RUNTIME_EXCEPTION,
    TIMEOUT,
    PROCESS_CRASH,
    CONFIGURATION,
    DEPENDENCY,
    ENVIRONMENT,
    UNKNOWN
}
