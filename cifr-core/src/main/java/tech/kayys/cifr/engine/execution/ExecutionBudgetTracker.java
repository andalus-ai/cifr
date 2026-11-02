package tech.kayys.cifr.engine.execution;

/**
 * Tracks budget consumption across execution steps, tools, and model calls.
 */
public interface ExecutionBudgetTracker {
    void recordStep();
    void recordToolCall();
    void recordModelCall();
    void recordReplan();
    void recordOutputTokens(long tokens);
    BudgetStatus evaluate(ExecutionBudget budget);
}
