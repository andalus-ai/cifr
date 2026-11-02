package tech.kayys.cifr.engine.tool.registry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.tool.protocol.CapabilityId;
import tech.kayys.cifr.engine.tool.protocol.CapabilityRegistry;
import tech.kayys.cifr.engine.tool.protocol.ToolCapability;
import java.util.*;
import java.util.stream.StreamSupport;
@ApplicationScoped
public class DefaultCapabilityRegistry implements CapabilityRegistry {
    private final Map<String, ToolCapability> capabilities = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    public DefaultCapabilityRegistry(Instance<ToolCapability> discovered) {
        discovered.forEach(cap -> capabilities.put(cap.id().value(), cap));
    }

    public DefaultCapabilityRegistry() {}

    public void register(ToolCapability capability) {
        capabilities.put(capability.id().value(), capability);
    }

    @Override public Optional<ToolCapability> find(CapabilityId id) {
        return Optional.ofNullable(capabilities.get(id.value()));
    }
    @Override public Collection<ToolCapability> all() {
        return Collections.unmodifiableCollection(capabilities.values());
    }
    @Override public Collection<ToolCapability> forPhase(String phase) {
        return capabilities.values().stream()
            .filter(c -> c.descriptor().metadata() != null)
            .toList();
    }
}
