package tech.kayys.aljabr.engine.tool.middleware;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.tool.request.ToolRequest;
import tech.kayys.aljabr.engine.tool.result.ToolResult;
public interface ToolMiddleware {
    Uni<ToolResult> intercept(ToolRequest request, ToolContext context, ToolChain chain);
    int order();
}
