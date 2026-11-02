package tech.kayys.cifr.engine.tool;

public enum ToolCapability {
    READ_ONLY,
    WRITE,
    DELETE,
    EXECUTE_PROCESS,
    NETWORK_ACCESS,
    MUTATES_WORKSPACE,
    MUTATES_GIT
}
