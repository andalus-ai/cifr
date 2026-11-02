package tech.kayys.cifr.engine.index.relation;

import java.nio.file.Path;

public record RelatedFileRequest(
    Path sourceFile,
    int limit
) {}
