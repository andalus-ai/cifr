package tech.kayys.aljabr.engine.exec;

public record ExecutionCost(
    int cpuCores,
    long memoryBytes,
    boolean isBuild,
    boolean isTest
) {}
