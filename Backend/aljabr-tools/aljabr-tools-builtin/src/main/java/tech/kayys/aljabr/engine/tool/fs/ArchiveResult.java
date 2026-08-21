package tech.kayys.aljabr.engine.tool.fs;

import tech.kayys.aljabr.engine.tool.ToolResult;
import java.util.List;

public record ArchiveResult(
    boolean success, 
    String message, 
    String path, 
    long sizeBytes, 
    int fileCount, 
    List<ArchiveEntryInfo> entries
) implements ToolResult {
    public ArchiveResult(boolean success, String message, String path, long sizeBytes, int fileCount) {
        this(success, message, path, sizeBytes, fileCount, null);
    }
    @Override public boolean isSuccess() { return success; }
    @Override public String getSummary() { return message; }
}
