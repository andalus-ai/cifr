package tech.kayys.aljabr.engine.tool.middleware.impl;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.tool.middleware.ToolChain;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
import tech.kayys.aljabr.engine.tool.middleware.ToolMiddleware;
import tech.kayys.aljabr.engine.tool.protocol.ToolCapability;
import tech.kayys.aljabr.engine.tool.request.ToolRequest;
import tech.kayys.aljabr.engine.tool.result.ToolResult;
import tech.kayys.aljabr.engine.tool.result.ToolStatus;
@ApplicationScoped
public class AuthorizationMiddleware implements ToolMiddleware {
    @Override public int order() { return 10; }
    @Override
    public Uni<ToolResult> intercept(ToolRequest request, ToolContext context, ToolChain chain) {
        // Simplified: check context has at least READ authority
        if (context.grantedAuthorities().isEmpty()) {
            return Uni.createFrom().item(ToolResult.failure("DENIED: no authority granted"));
        }
        return chain.proceed(request, context);
    }
}
