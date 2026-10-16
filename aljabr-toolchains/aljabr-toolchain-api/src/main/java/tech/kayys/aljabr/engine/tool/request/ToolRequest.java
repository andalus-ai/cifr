package tech.kayys.aljabr.engine.tool.request;
import tech.kayys.aljabr.engine.tool.protocol.CapabilityId;
import java.util.Map;
public record ToolRequest(
    ToolRequestId id,
    CapabilityId capability,
    Map<String, Object> arguments,
    ToolRequestOptions options
) {
    public ToolRequest { arguments = arguments != null ? Map.copyOf(arguments) : Map.of(); }
    public static ToolRequest of(CapabilityId capability, Map<String, Object> arguments) {
        return new ToolRequest(ToolRequestId.generate(), capability, arguments, ToolRequestOptions.defaults());
    }
}
