package tech.kayys.aljabr.engine.tool.fs;

public record ArchiveEntryInfo(String path, boolean directory, long size, long lastModified) {}
