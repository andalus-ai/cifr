package tech.kayys.cifr.engine.tool.result;

import java.time.Duration;

/**
 * Cost metrics for a single tool execution, used for local optimization telemetry.
 */
public record CostMetrics(
    Duration executionTime,
    long bytesRead,
    long bytesWritten,
    int outputTokensIfModeled,
    boolean cacheHit
) {
    public static CostMetrics zero() {
        return new CostMetrics(Duration.ZERO, 0L, 0L, 0, false);
    }

    public static CostMetrics cacheHit(Duration lookupTime) {
        return new CostMetrics(lookupTime, 0L, 0L, 0, true);
    }
}
