package tech.kayys.cifr.engine.tool.fs;

import tech.kayys.cifr.engine.tool.ToolResult;

public record WriteFileResult(boolean success, String message, FileMetadata metadata) implements ToolResult {
    @Override
    public boolean isSuccess() { return success; }

    @Override
    public String getSummary() { return message; }
}
