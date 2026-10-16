package tech.kayys.aljabr.engine.tool.middleware.impl;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.tool.middleware.ToolChain;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
import tech.kayys.aljabr.engine.tool.middleware.ToolMiddleware;
import tech.kayys.aljabr.engine.tool.request.ToolRequest;
import tech.kayys.aljabr.engine.tool.result.ToolResult;
import java.time.Duration;
@ApplicationScoped
public class TimeoutMiddleware implements ToolMiddleware {
    @Override public int order() { return 30; }
    @Override
    public Uni<ToolResult> intercept(ToolRequest request, ToolContext context, ToolChain chain) {
        long timeout = request.options() != null ? request.options().timeoutMs() : context.timeoutMs();
        return chain.proceed(request, context)
            .ifNoItem().after(Duration.ofMillis(timeout))
            .recoverWithItem(ToolResult.failure("TIMEOUT: tool execution exceeded " + timeout + "ms"));
    }
}
