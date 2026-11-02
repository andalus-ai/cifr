package tech.kayys.cifr.engine.tool.fs;

import tech.kayys.cifr.engine.tool.ToolResult;

public record RenameResult(boolean success, String message, String source, String destination, boolean isDirectory) implements ToolResult {
    public RenameResult(boolean success, String message) {
        this(success, message, null, null, false);
    }
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
