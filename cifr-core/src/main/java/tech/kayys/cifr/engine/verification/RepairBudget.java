package tech.kayys.cifr.engine.verification;

import java.time.Duration;

/**
 * Bounds the automated repair loop to prevent endless retry cycles on hard failures.
 */
public record RepairBudget(
    int maxAttempts,
    int maxRepeatedFailures,
    int maxIdenticalStrategies,
    Duration maxDuration
) {
    public static RepairBudget defaults() {
        return new RepairBudget(3, 2, 2, Duration.ofMinutes(10));
    }

    public static RepairBudget tight() {
        return new RepairBudget(2, 1, 1, Duration.ofMinutes(3));
    }
}
