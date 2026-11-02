package tech.kayys.cifr.engine.tool.git;

import tech.kayys.cifr.engine.tool.ToolResult;
import java.util.List;

public record GitStashResult(boolean success, String message, List<GitStashInfo> stashes) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
