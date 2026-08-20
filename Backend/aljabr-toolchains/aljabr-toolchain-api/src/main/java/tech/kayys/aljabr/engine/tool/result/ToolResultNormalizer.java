package tech.kayys.aljabr.engine.tool.result;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
public interface ToolResultNormalizer {
    Uni<NormalizedToolResult> normalize(ToolResult raw, ToolContext context);
}
