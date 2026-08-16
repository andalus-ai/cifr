package tech.kayys.aljabr.engine.patch;

import java.nio.file.Path;

public record PatchWarning(
    Path file,
    String code,
    String message
) {}
