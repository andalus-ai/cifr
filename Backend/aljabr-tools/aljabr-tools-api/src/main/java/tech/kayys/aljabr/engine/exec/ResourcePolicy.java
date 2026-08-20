package tech.kayys.aljabr.engine.exec;

import java.time.Duration;

public record ResourcePolicy(
    int maxCpu,
    long maxMemoryBytes,
    long maxDiskBytes,
    Duration maxRuntime
) {
    public static ResourcePolicy localM4Policy() {
        return new ResourcePolicy(
            4,
            4L * 1024 * 1024 * 1024, // 4 GB
            10L * 1024 * 1024 * 1024, // 10 GB
            Duration.ofMinutes(15)
        );
    }
}
