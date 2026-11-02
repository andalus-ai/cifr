package tech.kayys.cifr.engine.tool.sandbox;
import java.time.Duration;
import java.util.List;
public record SandboxPolicy(
    java.nio.file.Path workDir,
    Duration timeout,
    List<String> allowedBinaries,
    List<String> writePaths,
    boolean allowNetwork,
    long maxMemoryBytes
) {
    public SandboxPolicy { allowedBinaries = allowedBinaries != null ? List.copyOf(allowedBinaries) : List.of(); writePaths = writePaths != null ? List.copyOf(writePaths) : List.of(); }
    public static SandboxPolicy defaultLocal(java.nio.file.Path workDir) {
        return new SandboxPolicy(workDir, Duration.ofSeconds(60), List.of(), List.of(), false, 512 * 1024 * 1024L);
    }
}
