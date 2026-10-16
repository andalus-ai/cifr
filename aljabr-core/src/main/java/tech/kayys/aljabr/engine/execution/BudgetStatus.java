package tech.kayys.aljabr.engine.execution;

import java.time.Duration;
import java.util.Optional;

/**
 * Real-time status of budget consumption for an agent execution.
 */
public record BudgetStatus(
    boolean withinBudget,
    int stepsUsed,
    int toolCallsUsed,
    int modelCallsUsed,
    int replansUsed,
    Duration durationElapsed,
    long outputTokensUsed,
    Optional<String> exhaustionReason
) {
    public static BudgetStatus healthy(int steps, int tools, int models, int replans, Duration duration, long tokens) {
        return new BudgetStatus(true, steps, tools, models, replans, duration, tokens, Optional.empty());
    }

    public static BudgetStatus exhausted(int steps, int tools, int models, int replans, Duration duration, long tokens, String reason) {
        return new BudgetStatus(false, steps, tools, models, replans, duration, tokens, Optional.of(reason));
    }
}
