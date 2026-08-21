package tech.kayys.aljabr.engine.tool.analysis;

import tech.kayys.aljabr.engine.tool.ToolResult;
import java.util.List;

public record FormatToolResult(boolean success, String message, List<FormatResult> results) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
