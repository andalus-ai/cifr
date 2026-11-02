package tech.kayys.cifr.engine.patch;

import java.nio.file.Path;

public record PatchIssue(
    Path file,
    String code,
    String message
) {}
