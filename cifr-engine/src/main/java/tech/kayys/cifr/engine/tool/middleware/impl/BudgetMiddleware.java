package tech.kayys.cifr.engine.tool.middleware.impl;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.middleware.ToolChain;
import tech.kayys.cifr.engine.tool.middleware.ToolContext;
import tech.kayys.cifr.engine.tool.middleware.ToolMiddleware;
import tech.kayys.cifr.engine.tool.request.ToolRequest;
import tech.kayys.cifr.engine.tool.result.ToolResult;
@ApplicationScoped
public class BudgetMiddleware implements ToolMiddleware {
    @Override public int order() { return 20; }
    @Override
    public Uni<ToolResult> intercept(ToolRequest request, ToolContext context, ToolChain chain) {
        return chain.proceed(request, context);
    }
}
