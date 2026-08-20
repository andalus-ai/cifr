package tech.kayys.aljabr.engine.tool.middleware.impl;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.tool.middleware.ToolChain;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
import tech.kayys.aljabr.engine.tool.middleware.ToolMiddleware;
import tech.kayys.aljabr.engine.tool.request.ToolRequest;
import tech.kayys.aljabr.engine.tool.result.ToolResult;
import java.time.Instant;
@ApplicationScoped
public class AuditMiddleware implements ToolMiddleware {
    private static final Logger LOG = Logger.getLogger(AuditMiddleware.class);
    @Override public int order() { return 40; }
    @Override
    public Uni<ToolResult> intercept(ToolRequest request, ToolContext context, ToolChain chain) {
        Instant start = Instant.now();
        return chain.proceed(request, context)
            .invoke(result -> LOG.debugf("Tool %s completed in %dms, status=%s",
                request.capability().value(),
                java.time.Duration.between(start, Instant.now()).toMillis(),
                result.status()));
    }
}
