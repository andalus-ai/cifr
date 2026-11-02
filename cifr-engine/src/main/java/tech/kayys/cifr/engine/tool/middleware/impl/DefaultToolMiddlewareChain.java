package tech.kayys.cifr.engine.tool.middleware.impl;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.tool.middleware.ToolChain;
import tech.kayys.cifr.engine.tool.middleware.ToolContext;
import tech.kayys.cifr.engine.tool.middleware.ToolMiddleware;
import tech.kayys.cifr.engine.tool.request.ToolRequest;
import tech.kayys.cifr.engine.tool.result.ToolResult;
import java.util.Comparator;
import java.util.List;
@ApplicationScoped
public class DefaultToolMiddlewareChain {
    @Inject jakarta.enterprise.inject.Instance<ToolMiddleware> middlewares;

    public Uni<ToolResult> execute(ToolRequest request, ToolContext context) {
        List<ToolMiddleware> sorted = middlewares.stream()
            .sorted(Comparator.comparingInt(ToolMiddleware::order))
            .toList();
        return buildChain(sorted, 0).proceed(request, context);
    }

    private ToolChain buildChain(List<ToolMiddleware> middlewares, int index) {
        if (index >= middlewares.size()) {
            return (req, ctx) -> Uni.createFrom().item(ToolResult.failure("No executor in chain"));
        }
        ToolMiddleware current = middlewares.get(index);
        ToolChain next = buildChain(middlewares, index + 1);
        return (req, ctx) -> current.intercept(req, ctx, next);
    }
}
