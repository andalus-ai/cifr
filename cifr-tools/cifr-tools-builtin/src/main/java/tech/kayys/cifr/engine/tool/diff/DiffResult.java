package tech.kayys.cifr.engine.tool.diff;

import tech.kayys.cifr.engine.tool.ToolResult;
import java.util.List;

public record DiffResult(boolean success, String message, List<DiffBlock> blocks) implements ToolResult {
    @Override
    public boolean isSuccess() { return success; }
    @Override
    public String getSummary() { return message; }
}
