package tech.kayys.cifr.engine.tool.middleware;
import java.util.List;
public record ToolSelectionRequest(String goal, String phase, List<String> constraints) {
    public ToolSelectionRequest { constraints = constraints != null ? List.copyOf(constraints) : List.of(); }
}
