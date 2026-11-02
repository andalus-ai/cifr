package tech.kayys.cifr.engine.capability;

import java.util.List;
import java.util.Optional;

/**
 * Registry storing and discovering available agent capabilities.
 */
public interface CapabilityRegistry {

    Optional<Capability> find(String capabilityId);

    List<Capability> list();

    void register(Capability capability);
}
