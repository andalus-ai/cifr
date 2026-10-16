package tech.kayys.aljabr.engine.tool.ide;

import tech.kayys.aljabr.engine.tool.ToolResult;

public record EditorResult(boolean success, String message, String content) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
