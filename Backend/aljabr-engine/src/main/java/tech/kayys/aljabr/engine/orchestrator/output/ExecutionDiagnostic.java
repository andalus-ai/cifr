package tech.kayys.aljabr.engine.orchestrator.output;

import java.nio.file.Path;
import java.util.Optional;

public record ExecutionDiagnostic(
    Optional<Path> file,
    int line,
    String message,
    String severity,
    String category
) {
    public static ExecutionDiagnostic error(String message, String category) {
        return new ExecutionDiagnostic(Optional.empty(), 0, message, "ERROR", category);
    }

    public static ExecutionDiagnostic error(Path file, int line, String message, String category) {
        return new ExecutionDiagnostic(Optional.ofNullable(file), line, message, "ERROR", category);
    }

    public static ExecutionDiagnostic warning(Path file, int line, String message) {
        return new ExecutionDiagnostic(Optional.ofNullable(file), line, message, "WARNING", "LINT");
    }
}
