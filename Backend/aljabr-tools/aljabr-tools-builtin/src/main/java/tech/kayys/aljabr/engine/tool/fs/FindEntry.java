package tech.kayys.aljabr.engine.tool.fs;

public record FindEntry(String path, boolean isDirectory, long sizeBytes, long lastModified) {}
