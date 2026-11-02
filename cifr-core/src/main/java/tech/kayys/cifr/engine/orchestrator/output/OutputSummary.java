package tech.kayys.cifr.engine.orchestrator.output;

/**
 * A concise summary of orchestrated execution output, used in {@link ProcessedOutput}.
 */
public record OutputSummary(
    ExecutionStatus status,
    String summary,
    int exitCode,
    boolean hasErrors
) {
    public static OutputSummary success(String summary) {
        return new OutputSummary(ExecutionStatus.SUCCESS, summary, 0, false);
    }

    public static OutputSummary failure(String summary, int exitCode) {
        return new OutputSummary(ExecutionStatus.FAILURE, summary, exitCode, true);
    }
}
