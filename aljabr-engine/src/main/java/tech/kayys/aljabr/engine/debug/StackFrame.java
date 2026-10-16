package tech.kayys.aljabr.engine.debug;

import java.nio.file.Path;

public record StackFrame(
    String className,
    String methodName,
    Path file,
    int lineNumber
) {}
