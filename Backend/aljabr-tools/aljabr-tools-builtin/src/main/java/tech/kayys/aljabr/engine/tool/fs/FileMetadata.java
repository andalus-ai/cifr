package tech.kayys.aljabr.engine.tool.fs;

public record FileMetadata(
    long sizeBytes,
    long lastModified,
    boolean isReadOnly
) {}
