package tech.kayys.aljabr.engine.context;

public record ContextBudget(
    int totalBudgetTokens,
    int taskBudgetTokens,
    int targetBudgetTokens,
    int failureBudgetTokens,
    int snippetBudgetTokens,
    int dependencyBudgetTokens
) {
    public static ContextBudget defaultBudget() {
        return new ContextBudget(4000, 500, 500, 1000, 1500, 500);
    }

    public static ContextBudget forModel(String modelId) {
        if (modelId != null && (modelId.contains("27b") || modelId.contains("pro") || modelId.contains("claude"))) {
            return new ContextBudget(8000, 1000, 1000, 2000, 3000, 1000);
        }
        return defaultBudget();
    }
}
