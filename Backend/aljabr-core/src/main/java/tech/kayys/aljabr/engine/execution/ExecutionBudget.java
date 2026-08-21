package tech.kayys.aljabr.engine.execution;

import java.time.Duration;

/**
 * Multi-dimensional resource constraints governing an agent execution.
 */
public record ExecutionBudget(
    int maxSteps,
    int maxToolCalls,
    int maxModelCalls,
    int maxReplans,
    Duration maxDuration,
    long maxOutputTokens
) {
    public static ExecutionBudget standard() {
        return new ExecutionBudget(50, 40, 30, 5, Duration.ofMinutes(10), 32768L);
    }

    public static ExecutionBudget lightweight() {
        return new ExecutionBudget(20, 15, 10, 2, Duration.ofMinutes(3), 8192L);
    }
}
