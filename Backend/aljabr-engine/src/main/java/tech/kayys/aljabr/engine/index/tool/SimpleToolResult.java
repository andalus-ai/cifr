package tech.kayys.aljabr.engine.index.tool;

import tech.kayys.aljabr.engine.tool.ToolResult;

public record SimpleToolResult(
    boolean success,
    String summary
) implements ToolResult {
    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public String getSummary() {
        return summary;
    }
}
