package tech.kayys.cifr.engine.exec.intelligence;

import tech.kayys.cifr.engine.verify.Diagnostic;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public record OutputSummary(
    ProcessStatus status,
    int exitCode,
    Duration duration,
    FailureType failureType,
    List<Diagnostic> diagnostics,
    List<String> failedTests,
    List<String> passedTests,
    List<String> relevantSnippets,
    Optional<FailureFingerprint> fingerprint
) {
    public OutputSummary {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        failedTests = failedTests != null ? List.copyOf(failedTests) : List.of();
        passedTests = passedTests != null ? List.copyOf(passedTests) : List.of();
        relevantSnippets = relevantSnippets != null ? List.copyOf(relevantSnippets) : List.of();
    }
}
