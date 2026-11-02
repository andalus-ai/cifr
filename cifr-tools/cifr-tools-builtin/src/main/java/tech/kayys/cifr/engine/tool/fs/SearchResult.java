package tech.kayys.cifr.engine.tool.fs;

import java.util.List;
import tech.kayys.cifr.engine.tool.ToolResult;

public record SearchResult(
    String query,
    List<SearchMatch> matches,
    boolean truncated
) implements ToolResult {
    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public String getSummary() {
        return "Found " + matches.size() + " matches for query '" + query + "'";
    }
}
