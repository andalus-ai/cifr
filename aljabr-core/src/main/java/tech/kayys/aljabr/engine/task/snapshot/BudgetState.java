package tech.kayys.aljabr.engine.task.snapshot;

public record BudgetState(
    long tokenBudgetTotal,
    long tokenBudgetUsed,
    long timeBudgetSeconds,
    long timeElapsedSeconds,
    int toolCallBudget,
    int toolCallsUsed
) {
    public static BudgetState unlimited() {
        return new BudgetState(Long.MAX_VALUE, 0, Long.MAX_VALUE, 0, Integer.MAX_VALUE, 0);
    }

    public long tokenBudgetRemaining() {
        return tokenBudgetTotal - tokenBudgetUsed;
    }

    public int toolCallsRemaining() {
        return toolCallBudget - toolCallsUsed;
    }
}
