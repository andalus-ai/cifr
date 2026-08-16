package tech.kayys.aljabr.engine.orchestrator.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.orchestrator.ResolvedTool;
import tech.kayys.aljabr.engine.orchestrator.ToolIntent;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DefaultToolResolver implements ToolResolver {

    private final List<ToolAdapter> adapters = new ArrayList<>();

    @Inject
    public DefaultToolResolver(Instance<ToolAdapter> adapterInstances) {
        if (adapterInstances != null) {
            for (ToolAdapter adapter : adapterInstances) {
                adapters.add(adapter);
            }
        }
    }

    public DefaultToolResolver(List<ToolAdapter> adapters) {
        if (adapters != null) {
            this.adapters.addAll(adapters);
        }
    }

    @Override
    public Uni<ResolvedTool> resolve(ToolIntent intent, ProjectToolchain toolchain) {
        for (ToolAdapter adapter : adapters) {
            if (!(adapter instanceof GenericToolAdapter) && adapter.supports(toolchain)) {
                return adapter.resolve(intent, toolchain);
            }
        }

        // Fallback to generic adapter
        for (ToolAdapter adapter : adapters) {
            if (adapter instanceof GenericToolAdapter) {
                return adapter.resolve(intent, toolchain);
            }
        }

        return new GenericToolAdapter().resolve(intent, toolchain);
    }
}
