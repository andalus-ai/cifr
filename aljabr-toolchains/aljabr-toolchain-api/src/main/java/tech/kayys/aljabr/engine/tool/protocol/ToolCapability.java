package tech.kayys.aljabr.engine.tool.protocol;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
import tech.kayys.aljabr.engine.tool.request.ToolRequest;
import tech.kayys.aljabr.engine.tool.result.ToolResult;
public interface ToolCapability {
    CapabilityId id();
    ToolDescriptor descriptor();
    Uni<ToolResult> execute(ToolRequest request, ToolContext context);
}
