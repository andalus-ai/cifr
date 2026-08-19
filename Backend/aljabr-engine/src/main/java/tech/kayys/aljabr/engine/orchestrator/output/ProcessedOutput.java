package tech.kayys.aljabr.engine.orchestrator.output;

import java.util.List;
import tech.kayys.aljabr.engine.process.OutputArtifact;
import tech.kayys.aljabr.engine.verify.Diagnostic;

public record ProcessedOutput(
    OutputSummary summary,
    List<Diagnostic> diagnostics,
    List<OutputArtifact> artifacts
) {
    public ProcessedOutput {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        artifacts = artifacts != null ? List.copyOf(artifacts) : List.of();
    }
}
