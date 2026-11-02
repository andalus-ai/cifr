package tech.kayys.cifr.engine.exec.intelligence;

import tech.kayys.cifr.engine.verify.Diagnostic;

import java.util.List;
import java.util.Optional;

public record FailureAnalysis(
    FailureType failureType,
    String summary,
    List<Diagnostic> primaryDiagnostics,
    Optional<String> rootCauseHypothesis,
    boolean isActionable
) {
    public FailureAnalysis {
        primaryDiagnostics = primaryDiagnostics != null ? List.copyOf(primaryDiagnostics) : List.of();
    }
}
