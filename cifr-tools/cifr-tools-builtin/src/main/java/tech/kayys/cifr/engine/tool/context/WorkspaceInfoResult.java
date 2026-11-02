package tech.kayys.cifr.engine.tool.context;

import tech.kayys.cifr.engine.tool.ToolResult;
import java.util.Map;

public record WorkspaceInfoResult(boolean success, String message, Map<String, Object> details) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
