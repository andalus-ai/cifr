package tech.kayys.cifr.engine.exec.intelligence;

import tech.kayys.cifr.engine.verify.Diagnostic;

import java.util.List;
import java.util.Optional;

public record NormalizedOutput(
    ProcessStatus status,
    int exitCode,
    List<Diagnostic> diagnostics,
    List<String> failedTests,
    List<String> passedTests,
    Optional<String> primaryErrorMessage,
    List<String> relevantLogSnippets
) {
    public NormalizedOutput {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        failedTests = failedTests != null ? List.copyOf(failedTests) : List.of();
        passedTests = passedTests != null ? List.copyOf(passedTests) : List.of();
        relevantLogSnippets = relevantLogSnippets != null ? List.copyOf(relevantLogSnippets) : List.of();
    }
}
