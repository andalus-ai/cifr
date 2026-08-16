package tech.kayys.aljabr.engine.debug;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import tech.kayys.aljabr.engine.verify.Diagnostic;

public record Failure(
    FailureId id,
    FailureKind kind,
    Optional<Path> file,
    Optional<String> location,
    String message,
    List<StackFrame> stack,
    List<Diagnostic> diagnostics
) {
    public Failure {
        stack = stack != null ? List.copyOf(stack) : List.of();
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
    }
}
