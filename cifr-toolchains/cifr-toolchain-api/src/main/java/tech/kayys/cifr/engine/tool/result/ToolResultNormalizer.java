package tech.kayys.cifr.engine.tool.result;
import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.tool.middleware.ToolContext;
public interface ToolResultNormalizer {
    Uni<NormalizedToolResult> normalize(ToolResult raw, ToolContext context);
}
