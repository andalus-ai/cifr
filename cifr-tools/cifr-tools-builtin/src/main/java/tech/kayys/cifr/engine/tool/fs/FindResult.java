package tech.kayys.cifr.engine.tool.fs;

import tech.kayys.cifr.engine.tool.ToolResult;
import java.util.List;

public record FindResult(String pattern, List<FindEntry> entries, boolean truncated) implements ToolResult {
    @Override public boolean isSuccess() { return true; }
    @Override public String getSummary() {
        return "Found " + entries.size() + " match(es) for '" + pattern + "'" + (truncated ? " (truncated)" : "");
    }
}
