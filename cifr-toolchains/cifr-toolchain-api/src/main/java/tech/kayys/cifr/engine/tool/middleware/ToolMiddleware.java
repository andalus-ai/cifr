package tech.kayys.cifr.engine.tool.middleware;
import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.tool.request.ToolRequest;
import tech.kayys.cifr.engine.tool.result.ToolResult;
public interface ToolMiddleware {
    Uni<ToolResult> intercept(ToolRequest request, ToolContext context, ToolChain chain);
    int order();
}
