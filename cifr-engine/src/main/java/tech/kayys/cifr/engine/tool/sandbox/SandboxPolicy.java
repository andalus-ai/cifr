package tech.kayys.cifr.engine.tool.sandbox;

import java.time.Duration;
import java.util.List;

/**
 * Requested execution constraints for a process backend.
 *
 * <p>The local {@link DefaultExecutionSandbox} enforces timeout, working-directory
 * validity and a non-empty executable allowlist. It does not enforce
 * {@code writePaths}, {@code allowNetwork}, or {@code maxMemoryBytes}; those
 * require an OS/container sandbox backend. Do not treat this policy record or
 * the local ProcessBuilder runner as a security boundary.</p>
 */
public record SandboxPolicy(
        java.nio.file.Path workDir,
        Duration timeout,
        List<String> allowedBinaries,
        List<String> writePaths,
        boolean allowNetwork,
        long maxMemoryBytes
) {
    public SandboxPolicy {
        allowedBinaries = allowedBinaries != null ? List.copyOf(allowedBinaries) : List.of();
        writePaths = writePaths != null ? List.copyOf(writePaths) : List.of();
    }

    /**
     * Compatibility defaults for local development only. Empty executable
     * allowlist means unrestricted command selection in the local runner.
     */
    public static SandboxPolicy defaultLocal(java.nio.file.Path workDir) {
        return new SandboxPolicy(workDir, Duration.ofSeconds(60), List.of(), List.of(), false, 512 * 1024 * 1024L);
    }
}
