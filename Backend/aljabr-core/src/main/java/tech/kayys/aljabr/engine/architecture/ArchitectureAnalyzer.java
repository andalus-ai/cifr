package tech.kayys.aljabr.engine.architecture;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.knowledge.graph.RepositoryGraph;

import java.util.List;

public interface ArchitectureAnalyzer {
    Uni<List<ArchitectureViolation>> detectViolations(RepositoryGraph graph, List<ArchitectureRule> rules);
    List<ArchitectureRule> defaultRules();
}
