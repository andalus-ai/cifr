package tech.kayys.cifr.engine.tool.fs;

public record FindEntry(String path, boolean isDirectory, long sizeBytes, long lastModified) {}
