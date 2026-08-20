package tech.kayys.aljabr.engine.tool.index;

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
