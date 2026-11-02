package tech.kayys.cifr.engine.policy;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Default implementation of {@link PolicyEngine}.
 */
@ApplicationScoped
public class DefaultPolicyEngine implements PolicyEngine {

    @Inject
    PolicyAggregator aggregator;

    @Override
    public Uni<Decision> evaluate(PolicyContext context, PolicyRequest request) {
        return Uni.createFrom().item(() -> {
            // Default permissive evaluation within sandbox
            Decision d = Decision.allow("Permitted under default policy");
            return aggregator.combine(List.of(d));
        });
    }
}
