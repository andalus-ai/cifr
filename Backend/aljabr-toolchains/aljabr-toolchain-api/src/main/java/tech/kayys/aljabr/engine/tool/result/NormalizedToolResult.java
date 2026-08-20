package tech.kayys.aljabr.engine.tool.result;
import java.util.List;
public record NormalizedToolResult(String summary, List<String> keyFindings, boolean truncated, ToolResult raw) {
    public NormalizedToolResult { keyFindings = keyFindings != null ? List.copyOf(keyFindings) : List.of(); }
}
