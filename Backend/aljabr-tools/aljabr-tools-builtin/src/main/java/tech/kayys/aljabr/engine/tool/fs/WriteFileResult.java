package tech.kayys.aljabr.engine.tool.fs;

import tech.kayys.aljabr.engine.tool.ToolResult;

public record WriteFileResult(boolean success, String message, FileMetadata metadata) implements ToolResult {
    @Override
    public boolean isSuccess() { return success; }

    @Override
    public String getSummary() { return message; }
}
