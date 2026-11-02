package tech.kayys.cifr.engine.architecture;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.knowledge.graph.RepositoryGraph;

import java.util.List;

public interface ArchitectureAnalyzer {
    Uni<List<ArchitectureViolation>> detectViolations(RepositoryGraph graph, List<ArchitectureRule> rules);
    List<ArchitectureRule> defaultRules();
}
