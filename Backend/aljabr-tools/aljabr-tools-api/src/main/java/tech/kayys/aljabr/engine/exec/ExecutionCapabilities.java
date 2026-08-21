package tech.kayys.aljabr.engine.exec;

/**
 * Capabilities of an execution environment.
 * Defines what features and limitations the environment supports.
 */
public record ExecutionCapabilities(
        boolean supportsProcesses,
        boolean supportsInputRedirection,
        boolean supportsOutputCapture,
        boolean supportsTimeout,
        boolean supportsEnvironment,
        boolean supportsWorkingDirectory,
        boolean supportsCancellation,
        boolean supportsIsolation) {
    /**
     * Creates a default local execution environment capabilities.
     * Suitable for running on a local machine with standard OS process support.
     */
    public static ExecutionCapabilities localDefault() {
        return new ExecutionCapabilities(
                true, // supportsProcesses - we can spawn processes
                true, // supportsInputRedirection - can pipe input
                true, // supportsOutputCapture - can capture stdout/stderr
                true, // supportsTimeout - can enforce timeouts
                true, // supportsEnvironment - can set env vars
                true, // supportsWorkingDirectory - can set working dir
                true, // supportsCancellation - can kill processes
                false // supportsIsolation - no container-level isolation
        );
    }

    /**
     * Creates capabilities for a sandboxed/containerized environment.
     * More restrictive than localDefault.
     */
    public static ExecutionCapabilities sandboxed() {
        return new ExecutionCapabilities(
                true,
                false, // May not allow input redirection
                true,
                true,
                true,
                true,
                true,
                true // Has isolation
        );
    }

    /**
     * Creates capabilities for a cloud/remote execution environment.
     * May have limitations on process control.
     */
    public static ExecutionCapabilities remote() {
        return new ExecutionCapabilities(
                true,
                true,
                true,
                true,
                true,
                true,
                false, // May not support cancellation
                true);
    }
}