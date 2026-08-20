package tech.kayys.aljabr.engine.tool.protocol;
import java.util.Map;
public record ToolInputSchema(Map<String, Object> properties, java.util.List<String> required) {
    public ToolInputSchema { properties = properties != null ? Map.copyOf(properties) : Map.of(); required = required != null ? java.util.List.copyOf(required) : java.util.List.of(); }
    public static ToolInputSchema empty() { return new ToolInputSchema(Map.of(), java.util.List.of()); }
}
