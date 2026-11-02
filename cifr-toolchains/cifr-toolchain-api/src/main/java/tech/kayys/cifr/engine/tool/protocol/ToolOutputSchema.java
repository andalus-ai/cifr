package tech.kayys.cifr.engine.tool.protocol;
import java.util.Map;
public record ToolOutputSchema(Map<String, Object> properties) {
    public ToolOutputSchema { properties = properties != null ? Map.copyOf(properties) : Map.of(); }
    public static ToolOutputSchema empty() { return new ToolOutputSchema(Map.of()); }
}
