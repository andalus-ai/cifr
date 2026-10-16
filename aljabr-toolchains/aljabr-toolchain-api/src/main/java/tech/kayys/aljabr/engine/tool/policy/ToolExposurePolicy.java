package tech.kayys.aljabr.engine.tool.policy;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
import tech.kayys.aljabr.engine.tool.protocol.ToolCapability;
import tech.kayys.aljabr.engine.tool.protocol.ToolDescriptor;
import java.util.Collection;
import java.util.List;
public interface ToolExposurePolicy {
    Uni<List<ToolDescriptor>> expose(ToolContext context, Collection<ToolCapability> available);
}
