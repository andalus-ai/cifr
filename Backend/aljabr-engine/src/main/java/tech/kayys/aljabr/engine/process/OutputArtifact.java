package tech.kayys.aljabr.engine.process;

import java.net.URI;

public record OutputArtifact(
    URI uri,
    long byteCount,
    int lineCount,
    boolean truncated
) {}
