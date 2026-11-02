package tech.kayys.cifr.engine.tool.request;
import java.time.Duration;
public record ToolBudget(int maxCalls, Duration maxExecutionTime, long maxOutputTokens, long maxInputBytes) {
    public static ToolBudget defaults() { return new ToolBudget(50, Duration.ofMinutes(5), 4096L, 1024 * 1024L); }
    public static ToolBudget local() { return new ToolBudget(30, Duration.ofMinutes(3), 2048L, 512 * 1024L); }
}
