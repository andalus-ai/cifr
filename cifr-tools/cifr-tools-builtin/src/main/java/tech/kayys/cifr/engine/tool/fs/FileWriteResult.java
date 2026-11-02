package tech.kayys.cifr.engine.tool.fs;

public record FileWriteResult(String path, boolean success, String message, long size, long lastModified) {}
