package tech.kayys.aljabr.engine.tool.policy;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
import tech.kayys.aljabr.engine.tool.protocol.ToolCapability;
import tech.kayys.aljabr.engine.tool.protocol.ToolDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
public class ModelAwareExposurePolicy implements ToolExposurePolicy {
    private final int maxVisibleTools;
    public ModelAwareExposurePolicy(int maxVisibleTools) { this.maxVisibleTools = maxVisibleTools; }
    public static ModelAwareExposurePolicy local() { return new ModelAwareExposurePolicy(6); }
    public static ModelAwareExposurePolicy cloud() { return new ModelAwareExposurePolicy(20); }
    @Override
    public Uni<List<ToolDescriptor>> expose(ToolContext context, Collection<ToolCapability> available) {
        List<ToolDescriptor> descriptors = available.stream()
            .filter(c -> c.descriptor().metadata().requiredAuthority().ordinal() <= authorityLevel(context))
            .limit(maxVisibleTools)
            .map(ToolCapability::descriptor)
            .collect(Collectors.toList());
        return Uni.createFrom().item(descriptors);
    }
    private int authorityLevel(ToolContext ctx) {
        if (ctx.grantedAuthorities().isEmpty()) return 0;
        return ctx.grantedAuthorities().stream().mapToInt(Enum::ordinal).max().orElse(0);
    }
}
