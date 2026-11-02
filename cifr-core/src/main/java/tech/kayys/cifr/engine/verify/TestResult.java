package tech.kayys.cifr.engine.verify;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public record TestResult(
    String testName,
    boolean passed,
    Duration duration,
    Optional<String> failureMessage,
    List<Diagnostic> diagnostics
) {
    public TestResult {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}
