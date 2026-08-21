package tech.kayys.aljabr.engine.tool.fs;

import tech.kayys.aljabr.engine.tool.ToolResult;
import java.util.List;

public record DirectoryListResult(String directory, List<DirectoryEntry> entries, boolean truncated) implements ToolResult {
    @Override public boolean isSuccess() { return true; }
    @Override public String getSummary() {
        return "Listed " + entries.size() + " entries in '" + directory + "'" + (truncated ? " (truncated)" : "");
    }
}
