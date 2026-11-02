package tech.kayys.cifr.engine.plan;

import java.time.Duration;

public record LoopBudget(
    int maxIterations,
    int maxModelCalls,
    int maxToolCalls,
    int maxReplans,
    Duration maxDuration
) {
    public static LoopBudget localDefault() {
        return new LoopBudget(30, 20, 50, 5, Duration.ofMinutes(15));
    }
}
