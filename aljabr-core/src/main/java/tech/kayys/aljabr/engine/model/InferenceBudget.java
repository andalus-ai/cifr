package tech.kayys.aljabr.engine.model;

/**
 * Resource and execution budget for a single model inference call.
 */
public record InferenceBudget(
    long maxInputTokens,
    long maxOutputTokens,
    long maxLatencyMs,
    int maxRetries
) {
    public static InferenceBudget localDefault() {
        return new InferenceBudget(8192L, 2048L, 30_000L, 2);
    }

    public static InferenceBudget cloudDefault() {
        return new InferenceBudget(32768L, 4096L, 60_000L, 3);
    }
}
