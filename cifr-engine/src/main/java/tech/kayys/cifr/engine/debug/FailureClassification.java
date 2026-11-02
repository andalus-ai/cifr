package tech.kayys.cifr.engine.debug;

import java.nio.file.Path;
import java.util.Optional;

public record FailureClassification(
    FailureKind kind,
    Optional<Path> suspectedFile,
    Optional<Integer> suspectedLine,
    String rootCauseSummary
) {}
