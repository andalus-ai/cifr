package tech.kayys.aljabr.engine.index;

import java.util.List;

/**
 * Request to resolve concise repository context for a specific reasoning state within a token budget.
 */
public record RepositoryContextRequest(
    String phase,
    String taskDescription,
    List<String> targetSymbols,
    long maxTokens
) {
    public RepositoryContextRequest {
        targetSymbols = targetSymbols != null ? List.copyOf(targetSymbols) : List.of();
    }

    public static RepositoryContextRequest forTask(String phase, String taskDescription, long maxTokens) {
        return new RepositoryContextRequest(phase, taskDescription, List.of(), maxTokens);
    }
}
