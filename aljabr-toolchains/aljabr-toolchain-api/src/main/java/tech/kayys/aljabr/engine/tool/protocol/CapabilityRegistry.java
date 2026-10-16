package tech.kayys.aljabr.engine.tool.protocol;
import java.util.Collection;
import java.util.Optional;
public interface CapabilityRegistry {
    Optional<ToolCapability> find(CapabilityId id);
    Collection<ToolCapability> all();
    Collection<ToolCapability> forPhase(String phase);
}
