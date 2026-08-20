package tech.kayys.aljabr.engine.patch.mutation;

import java.util.Set;

public record MutationPolicy(
    Set<String> allowedPaths,
    Set<String> deniedPaths,
    int maxFiles,
    int maxAddedLines,
    int maxDeletedLines,
    boolean requireUserForCriticalRisk
) {
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
