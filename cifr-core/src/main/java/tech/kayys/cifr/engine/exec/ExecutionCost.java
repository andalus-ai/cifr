package tech.kayys.cifr.engine.exec;

import java.time.Duration;

public record ExecutionCost(
    Duration wallTime,
    Duration cpuTime,
    long memoryBytes,
    int exitCode
) {}
