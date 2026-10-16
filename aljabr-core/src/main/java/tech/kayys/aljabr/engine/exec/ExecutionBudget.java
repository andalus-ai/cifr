package tech.kayys.aljabr.engine.exec;

import java.time.Duration;

public record ExecutionBudget(
    Duration maxDuration,
    long maxOutputBytes,
    long maxMemoryBytes,
    int maxProcesses
) {}
