package tech.kayys.aljabr.engine.orchestrator.output;

import java.util.List;

public record ProcessedOutput(
    String summary,
    List<ExecutionDiagnostic> diagnostics,
    String snippet,
    boolean isTruncated,
    ExecutionFailureType failureType
) {
    public ProcessedOutput {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    public static ProcessedOutput clean(String summary, String snippet) {
        return new ProcessedOutput(summary, List.of(), snippet, false, null);
    }
}
