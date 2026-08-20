package tech.kayys.aljabr.engine.verification;

import java.util.List;

/**
 * Compact repair context provided to the model when verification fails.
 * Gives failure knowledge and past failed approaches rather than raw logs.
 */
public record RepairContext(
    String taskDescription,
    TestFailureSummary failure,
    List<String> failedApproaches,
    String relevantSourceSnippet,
    int attemptNumber
) {
    public RepairContext {
        failedApproaches = failedApproaches != null ? List.copyOf(failedApproaches) : List.of();
    }
}
