package tech.kayys.cifr.engine.tool.fs;

import tech.kayys.cifr.engine.tool.ToolResult;

public record CopyResult(boolean success, String message, boolean isDirectory) implements ToolResult {
    public CopyResult(boolean success, String message) { this(success, message, false); }
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
