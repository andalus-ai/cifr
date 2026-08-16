package tech.kayys.aljabr.engine.process;

import java.time.Duration;
import java.util.Set;

public record ExecutionPolicy(
    Set<String> allowedExecutables,
    Set<String> deniedExecutables,
    Set<ExecutionProfile> allowedProfiles,
    boolean networkAllowed,
    boolean filesystemWriteAllowed,
    Duration maxTimeout,
    long maxOutputBytes,
    int maxProcesses
) {
    public static ExecutionPolicy defaultLocalPolicy() {
        return new ExecutionPolicy(
            Set.of("git", "mvn", "gradle", "npm", "node", "python", "pytest", "javac", "java"),
            Set.of("sudo", "rm -rf /", "shutdown", "reboot"),
            Set.of(ExecutionProfile.values()),
            false,
            true,
            Duration.ofMinutes(10),
            10 * 1024 * 1024L,
            5
        );
    }
}
