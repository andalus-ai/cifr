package tech.kayys.cifr.engine.tool.fs;

import tech.kayys.cifr.engine.tool.ToolResult;
import java.util.List;

public record BatchWriteResult(boolean success, String message, List<FileWriteResult> results, List<FileWriteResult> errors) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() {
        return message + " (wrote " + results.size() + " files" + (errors.isEmpty() ? "" : ", " + errors.size() + " errors") + ")";
    }
}
