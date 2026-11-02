package tech.kayys.cifr.engine.patch.mutation;

import java.util.Set;

public record MutationPolicy(
    Set<String> allowedPaths,
    Set<String> deniedPaths,
    int maxFiles,
    int maxAddedLines,
    int maxDeletedLines,
    boolean requireUserForCriticalRisk
) {
    public MutationPolicy {
        allowedPaths = allowedPaths != null ? Set.copyOf(allowedPaths) : Set.of();
        deniedPaths = deniedPaths != null ? Set.copyOf(deniedPaths) : Set.of();
        if (maxFiles < 1 || maxAddedLines < 0 || maxDeletedLines < 0) {
            throw new IllegalArgumentException("Mutation budgets must be non-negative and maxFiles must be positive");
        }
    }

    public static MutationPolicy defaultPolicy() {
        return new MutationPolicy(
            Set.of("src/**", "test/**", "docs/**"),
            Set.of(".git/**", "secrets/**", "build/**", "target/**"),
            10,
            500,
            250,
            true
        );
    }
}
