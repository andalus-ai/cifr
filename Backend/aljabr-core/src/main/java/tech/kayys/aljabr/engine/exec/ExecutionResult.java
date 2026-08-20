package tech.kayys.aljabr.engine.exec;

import java.util.List;
import java.util.Optional;
import tech.kayys.aljabr.engine.process.OutputArtifact;
import tech.kayys.aljabr.engine.verify.Diagnostic;

public record ExecutionResult(
    int exitCode,
    String stdoutSummary,
    String stderrSummary,
    List<Diagnostic> diagnostics,
    Optional<OutputArtifact> fullOutput,
    boolean timedOut
) {
    public ExecutionResult {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }
}
