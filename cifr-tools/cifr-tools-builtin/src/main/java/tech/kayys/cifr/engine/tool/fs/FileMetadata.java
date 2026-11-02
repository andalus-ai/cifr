package tech.kayys.cifr.engine.tool.fs;

public record FileMetadata(
    long sizeBytes,
    long lastModified,
    boolean isReadOnly
) {}
