package tech.kayys.cifr.engine.tool.git;

import tech.kayys.cifr.engine.tool.ToolResult;

public record GitCommitResult(boolean success, String message, String commitHash, String output) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message + (commitHash != null ? " [" + commitHash.substring(0, Math.min(7, commitHash.length())) + "]" : ""); }
}
