package tech.kayys.cifr.engine.tool.middleware;
import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.tool.request.ToolRequest;
import tech.kayys.cifr.engine.tool.result.ToolResult;
public interface ToolChain {
    Uni<ToolResult> proceed(ToolRequest request, ToolContext context);
}
