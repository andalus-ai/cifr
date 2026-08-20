package tech.kayys.aljabr.engine.tool.request;
import java.util.List;
public record ToolBatch(List<ToolRequest> requests, List<ToolDependency> dependencies) {
    public ToolBatch { requests = requests != null ? List.copyOf(requests) : List.of(); dependencies = dependencies != null ? List.copyOf(dependencies) : List.of(); }
    public static ToolBatch parallel(List<ToolRequest> requests) { return new ToolBatch(requests, List.of()); }
}
