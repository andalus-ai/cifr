package tech.kayys.aljabr.engine.process;

import java.net.URI;

public record OutputArtifact(
    URI uri,
    long sizeBytes,
    int lineCount,
    boolean isTruncated
) {}
