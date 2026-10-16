package tech.kayys.aljabr.engine.process;

import java.time.Duration;

public record ExecutionPolicy(
    Duration timeout,
    long maxMemoryBytes,
    int maxCpuCores
) {}
