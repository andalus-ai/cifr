package tech.kayys.aljabr.engine.tool.context;

import tech.kayys.aljabr.engine.tool.ToolResult;
import java.util.Map;

public record WorkspaceInfoResult(boolean success, String message, Map<String, Object> details) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
