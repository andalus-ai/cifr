package tech.kayys.cifr.engine.tool.protocol;
import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.tool.middleware.ToolContext;
import tech.kayys.cifr.engine.tool.request.ToolRequest;
import tech.kayys.cifr.engine.tool.result.ToolResult;
public interface ToolCapability {
    CapabilityId id();
    ToolDescriptor descriptor();
    Uni<ToolResult> execute(ToolRequest request, ToolContext context);
}
