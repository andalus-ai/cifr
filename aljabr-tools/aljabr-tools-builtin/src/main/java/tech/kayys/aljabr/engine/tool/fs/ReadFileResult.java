package tech.kayys.aljabr.engine.tool.fs;

import java.util.List;
import tech.kayys.aljabr.engine.tool.ToolResult;

public record ReadFileResult(
    String path,
    int startLine,
    int endLine,
    List<SourceLine> lines,
    FileMetadata metadata
) implements ToolResult {
    @Override
    public boolean isSuccess() {
        return true; // Assuming successful creation implies success
    }

    @Override
    public String getSummary() {
        return "Read " + lines.size() + " lines from " + path;
    }
}
