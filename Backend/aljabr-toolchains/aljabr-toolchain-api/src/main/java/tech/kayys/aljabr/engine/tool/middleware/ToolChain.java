package tech.kayys.aljabr.engine.tool.middleware;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.tool.request.ToolRequest;
import tech.kayys.aljabr.engine.tool.result.ToolResult;
public interface ToolChain {
    Uni<ToolResult> proceed(ToolRequest request, ToolContext context);
}
