package tech.kayys.cifr.engine.architecture;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.knowledge.graph.RepositoryGraph;

import java.util.*;

@ApplicationScoped
public class DefaultArchitectureAnalyzer implements ArchitectureAnalyzer {
    private static final Logger LOG = Logger.getLogger(DefaultArchitectureAnalyzer.class);

    @Override
    public List<ArchitectureRule> defaultRules() {
        return List.of(
                ArchitectureRule.disallow(LayerOrder.DOMAIN, LayerOrder.APPLICATION, "Domain must not depend on Application layer"),
                ArchitectureRule.disallow(LayerOrder.DOMAIN, LayerOrder.API, "Domain must not depend on API layer"),
                ArchitectureRule.disallow(LayerOrder.DOMAIN, LayerOrder.INFRASTRUCTURE, "Domain must not depend on Infrastructure directly (dependency inversion)"),
                ArchitectureRule.disallow(LayerOrder.API, LayerOrder.INFRASTRUCTURE, "API must not bypass domain/application to talk directly to Infrastructure")
        );
    }

    @Override
    public Uni<List<ArchitectureViolation>> detectViolations(RepositoryGraph graph, List<ArchitectureRule> rules) {
        return Uni.createFrom().item(() -> {
            if (graph == null) return List.of();
            List<ArchitectureRule> activeRules = rules != null ? rules : defaultRules();
            List<ArchitectureViolation> violations = new ArrayList<>();

            for (Map.Entry<String, Set<String>> edgeEntry : graph.outgoingEdges().entrySet()) {
                String source = edgeEntry.getKey();
                LayerOrder sourceLayer = inferLayer(source);
                if (sourceLayer == null) continue;

                for (String target : edgeEntry.getValue()) {
                    LayerOrder targetLayer = inferLayer(target);
                    if (targetLayer == null) continue;

                    for (ArchitectureRule rule : activeRules) {
                        if (!rule.allowed() && rule.fromLayer() == sourceLayer && rule.toLayer() == targetLayer) {
                            violations.add(new ArchitectureViolation(
                                    source,
                                    target,
                                    rule,
                                    String.format("Architecture violation: %s (%s) -> %s (%s): %s",
                                            source, sourceLayer, target, targetLayer, rule.description())
                            ));
                        }
                    }
                }
            }

            LOG.infof("Architecture analysis found %d violations", violations.size());
            return violations;
        });
    }

    private LayerOrder inferLayer(String nodeId) {
        if (nodeId == null) return null;
        String lower = nodeId.toLowerCase(Locale.ROOT);
        if (lower.contains(".api.") || lower.contains("/api/") || lower.startsWith("api.")) {
            return LayerOrder.API;
        }
        if (lower.contains(".domain.") || lower.contains("/domain/") || lower.startsWith("domain.")) {
            return LayerOrder.DOMAIN;
        }
        if (lower.contains(".application.") || lower.contains(".service.") || lower.contains("/service/") || lower.startsWith("app.")) {
            return LayerOrder.APPLICATION;
        }
        if (lower.contains(".infrastructure.") || lower.contains(".infra.") || lower.contains("/infra/") || lower.contains(".adapter.")) {
            return LayerOrder.INFRASTRUCTURE;
        }
        return null;
    }
}
