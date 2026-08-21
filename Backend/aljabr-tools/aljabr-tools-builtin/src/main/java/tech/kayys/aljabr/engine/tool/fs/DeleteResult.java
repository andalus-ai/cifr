package tech.kayys.aljabr.engine.tool.fs;

import tech.kayys.aljabr.engine.tool.ToolResult;
import java.util.List;

public record DeleteResult(boolean success, String message, List<DeleteRecord> deleted, List<String> errors) implements ToolResult {
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
