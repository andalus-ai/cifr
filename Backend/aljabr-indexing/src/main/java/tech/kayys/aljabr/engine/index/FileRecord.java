package tech.kayys.aljabr.engine.index;

import java.nio.file.Path;

public record FileRecord(
    Path path,
    String contentHash,
    long size,
    String language,
    boolean generated,
    boolean test,
    FileRole role
) {}
