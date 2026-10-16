package tech.kayys.aljabr.engine.orchestrator;

import java.time.Duration;

public record ToolOrchestrationPolicy(
    Duration timeout,
    long maxOutputBytes,
    boolean allowArbitraryCommands
) {
    public static ToolOrchestrationPolicy defaultPolicy() {
        return new ToolOrchestrationPolicy(Duration.ofMinutes(5), 10 * 1024 * 1024L, false);
    }
}
