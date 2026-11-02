package tech.kayys.cifr.engine.verification;

import java.time.Duration;

/**
 * Estimated cost of running a verification stage.
 */
public record VerificationCost(
    Duration estimatedDuration,
    int estimatedOutputTokens,
    long estimatedMemoryMb
) {
    public static VerificationCost instant() {
        return new VerificationCost(Duration.ofMillis(100), 50, 64);
    }

    public static VerificationCost targeted() {
        return new VerificationCost(Duration.ofSeconds(5), 500, 256);
    }

    public static VerificationCost heavy() {
        return new VerificationCost(Duration.ofMinutes(2), 5000, 1024);
    }
}
