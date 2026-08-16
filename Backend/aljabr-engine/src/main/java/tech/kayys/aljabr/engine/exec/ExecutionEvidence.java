package tech.kayys.aljabr.engine.exec;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import tech.kayys.aljabr.engine.process.OutputArtifact;
import tech.kayys.aljabr.engine.verify.Diagnostic;

public record ExecutionEvidence(
    String executionId,
    String commandSummary,
    ExecutionStatus status,
    List<Diagnostic> diagnostics,
    Optional<OutputArtifact> output,
    Duration duration
) {
    public ExecutionEvidence {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}
