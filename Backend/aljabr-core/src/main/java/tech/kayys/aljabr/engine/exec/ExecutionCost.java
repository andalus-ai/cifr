package tech.kayys.aljabr.engine.exec;

import java.time.Duration;

public record ExecutionCost(
    Duration wallTime,
    Duration cpuTime,
    long memoryBytes,
    int exitCode
) {}
