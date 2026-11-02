package tech.kayys.cifr.engine.tool.middleware.impl;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.middleware.ToolChain;
import tech.kayys.cifr.engine.tool.middleware.ToolContext;
import tech.kayys.cifr.engine.tool.middleware.ToolMiddleware;
import tech.kayys.cifr.engine.tool.protocol.ToolCapability;
import tech.kayys.cifr.engine.tool.request.ToolRequest;
import tech.kayys.cifr.engine.tool.result.ToolResult;
import tech.kayys.cifr.engine.tool.result.ToolStatus;
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
