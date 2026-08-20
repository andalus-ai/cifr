package tech.kayys.aljabr.engine.tool.middleware;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.tool.protocol.ToolDescriptor;
import java.util.List;
public interface CapabilityRouter {
    Uni<List<ToolDescriptor>> select(ToolSelectionRequest request, ToolContext context);
}
