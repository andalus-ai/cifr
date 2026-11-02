package tech.kayys.cifr.engine.index.search;

import java.nio.file.Path;

public record SearchMatch(
    Path file,
    int lineNumber,
    String snippet,
    double score
) {}
