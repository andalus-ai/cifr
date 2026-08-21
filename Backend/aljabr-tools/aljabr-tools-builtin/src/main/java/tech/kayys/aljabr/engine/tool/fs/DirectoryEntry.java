package tech.kayys.aljabr.engine.tool.fs;

public record DirectoryEntry(String path, boolean isDirectory, long sizeBytes, long lastModified) {}
