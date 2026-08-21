package tech.kayys.aljabr.engine.tool.generate;

import tech.kayys.aljabr.engine.tool.ToolResult;

public record GenerateResult(boolean success, String message, String path) implements ToolResult {
    public GenerateResult(boolean success, String message) {
        this(success, message, null);
    }
    @Override
    public boolean isSuccess() { return success; }
    @Override
    public String getSummary() { return message; }
}
