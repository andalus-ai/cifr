package tech.kayys.aljabr.engine.tool.fs;

public record PatchResult(String path, boolean success, String message, int replacements) {}
