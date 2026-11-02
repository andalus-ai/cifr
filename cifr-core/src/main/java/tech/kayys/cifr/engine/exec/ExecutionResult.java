package tech.kayys.cifr.engine.exec;

import java.util.List;
import java.util.Optional;
import tech.kayys.cifr.engine.process.OutputArtifact;
import tech.kayys.cifr.engine.verify.Diagnostic;

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

    public ExecutionResult(int exitCode, String stdoutSummary, String stderrSummary, boolean timedOut) {
        this(exitCode, stdoutSummary, stderrSummary, List.of(), Optional.empty(), timedOut);
    }

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }
}
