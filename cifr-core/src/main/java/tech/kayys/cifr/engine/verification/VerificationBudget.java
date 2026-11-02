package tech.kayys.cifr.engine.verification;

import java.time.Duration;

/**
 * Budget constraints governing the verification engine to prevent runaway test execution.
 */
public record VerificationBudget(
    Duration maxExecutionTime,
    long maxOutputBytes,
    int maxStages,
    int maxEscalations
) {
    public static VerificationBudget defaults() {
        return new VerificationBudget(Duration.ofMinutes(5), 2 * 1024 * 1024L, 4, 2);
    }

    public static VerificationBudget fast() {
        return new VerificationBudget(Duration.ofSeconds(30), 512 * 1024L, 3, 1);
    }
}
