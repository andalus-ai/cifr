package tech.kayys.cifr.engine.tool.git;

import tech.kayys.cifr.engine.tool.ToolResult;
import java.util.List;

public record GitBranchResult(boolean success, String message, List<GitBranchInfo> branches) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
