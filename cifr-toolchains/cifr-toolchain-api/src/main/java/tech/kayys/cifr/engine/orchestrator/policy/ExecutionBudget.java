package tech.kayys.cifr.engine.orchestrator.policy;

import java.time.Duration;

public record ExecutionBudget(
    Duration timeout,
    long maxOutputBytes,
    long maxMemoryBytes,
    int maxProcesses
) {
    public static ExecutionBudget defaultBudget() {
        return new ExecutionBudget(Duration.ofSeconds(60), 1024 * 1024, 512 * 1024 * 1024L, 4);
    }

    public static ExecutionBudget testBudget() {
        return new ExecutionBudget(Duration.ofSeconds(30), 512 * 1024, 256 * 1024 * 1024L, 2);
    }

    public static ExecutionBudget quickBudget() {
        return new ExecutionBudget(Duration.ofSeconds(10), 128 * 1024, 128 * 1024 * 1024L, 1);
    }
}
