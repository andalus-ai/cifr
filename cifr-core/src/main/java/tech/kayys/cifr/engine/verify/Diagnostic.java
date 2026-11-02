package tech.kayys.cifr.engine.verify;

import java.nio.file.Path;

public record Diagnostic(
    String severity,
    Path file,
    int line,
    int column,
    String message
) {}
