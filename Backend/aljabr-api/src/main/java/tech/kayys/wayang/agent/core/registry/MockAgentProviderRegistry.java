package tech.kayys.wayang.agent.core.registry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
@Alternative
@Priority(1)
public class MockAgentProviderRegistry extends AgentProviderRegistry {
    
    @Override
    void onStart(StartupEvent ev) {
        // Do nothing to prevent GollekSdk crash
    }
}
