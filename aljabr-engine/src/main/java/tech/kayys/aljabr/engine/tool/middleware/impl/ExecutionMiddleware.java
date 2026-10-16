package tech.kayys.aljabr.engine.tool.middleware.impl;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.tool.middleware.ToolChain;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
import tech.kayys.aljabr.engine.tool.middleware.ToolMiddleware;
import tech.kayys.aljabr.engine.tool.protocol.CapabilityRegistry;
import tech.kayys.aljabr.engine.tool.request.ToolRequest;
import tech.kayys.aljabr.engine.tool.result.ToolResult;
@ApplicationScoped
public class ExecutionMiddleware implements ToolMiddleware {
    @Inject CapabilityRegistry registry;
    @Override public int order() { return 100; }
    @Override
    public Uni<ToolResult> intercept(ToolRequest request, ToolContext context, ToolChain chain) {
        return registry.find(request.capability())
            .map(cap -> cap.execute(request, context))
            .orElse(Uni.createFrom().item(ToolResult.failure("Capability not found: " + request.capability().value())));
    }
}
