package tech.kayys.cifr.engine.tool.result;
import java.time.Duration;
import tech.kayys.cifr.engine.tool.protocol.ToolFailureCategory;
public record ToolMetrics(
    Duration duration,
    long inputBytes,
    long outputBytes,
    boolean cacheHit,
    int retries,
    ToolFailureCategory failure
) {
    public static ToolMetrics success(Duration duration, long inputBytes, long outputBytes) {
        return new ToolMetrics(duration, inputBytes, outputBytes, false, 0, null);
    }
    public static ToolMetrics cached(Duration duration) {
        return new ToolMetrics(duration, 0, 0, true, 0, null);
    }
}
