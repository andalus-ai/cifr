package tech.kayys.aljabr.engine.capability;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe default implementation of {@link CapabilityRegistry}.
 */
@ApplicationScoped
public class DefaultCapabilityRegistry implements CapabilityRegistry {

    private final Map<String, Capability> capabilities = new ConcurrentHashMap<>();

    public DefaultCapabilityRegistry() {
        // Register Andalus high-level agent capabilities
        register(Capability.of("coding", "Autonomous Coding", CapabilityCategory.EXECUTE, CapabilityRisk.HIGH));
        register(Capability.of("filesystem", "Filesystem Operations", CapabilityCategory.READ, CapabilityRisk.SAFE));
        register(Capability.of("shell", "Shell & Process Execution", CapabilityCategory.EXECUTE, CapabilityRisk.HIGH));
        register(Capability.of("git", "Git Version Control", CapabilityCategory.WRITE, CapabilityRisk.MEDIUM));
        register(Capability.of("testing", "Test Execution & Intelligence", CapabilityCategory.EXECUTE, CapabilityRisk.MEDIUM));
        register(Capability.of("debugging", "Debug & Fault Localization", CapabilityCategory.EXECUTE, CapabilityRisk.MEDIUM));
        register(Capability.of("browser", "Web & Browser Automation", CapabilityCategory.NETWORK, CapabilityRisk.LOW));

        // Register default granular capabilities
        register(Capability.of("filesystem.read", "Read File", CapabilityCategory.READ, CapabilityRisk.SAFE));
        register(Capability.of("filesystem.write", "Write File", CapabilityCategory.WRITE, CapabilityRisk.MEDIUM));
        register(Capability.of("process.execute", "Execute Process", CapabilityCategory.EXECUTE, CapabilityRisk.HIGH));
        register(Capability.of("git.diff", "Git Diff", CapabilityCategory.READ, CapabilityRisk.SAFE));
        register(Capability.of("git.commit", "Git Commit", CapabilityCategory.WRITE, CapabilityRisk.MEDIUM));
        register(Capability.of("web.search", "Web Search", CapabilityCategory.NETWORK, CapabilityRisk.LOW));
    }

    @Override
    public Optional<Capability> find(String capabilityId) {
        return Optional.ofNullable(capabilities.get(capabilityId));
    }

    @Override
    public List<Capability> list() {
        return new ArrayList<>(capabilities.values());
    }

    @Override
    public void register(Capability capability) {
        if (capability != null) {
            capabilities.put(capability.id(), capability);
        }
    }
}
