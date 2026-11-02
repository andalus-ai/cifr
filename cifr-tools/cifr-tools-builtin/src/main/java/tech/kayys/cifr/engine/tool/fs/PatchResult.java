package tech.kayys.cifr.engine.tool.fs;

public record PatchResult(String path, boolean success, String message, int replacements) {}
