package tech.kayys.cifr.engine.tool.fs;

public record ArchiveEntryInfo(String path, boolean directory, long size, long lastModified) {}
