package tech.kayys.aljabr.engine.tool.fs;

public record FileWriteResult(String path, boolean success, String message, long size, long lastModified) {}
