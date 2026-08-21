package tech.kayys.aljabr.engine.test;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.index.impact.ImpactReport;
import tech.kayys.aljabr.engine.knowledge.graph.RepositoryGraph;
import tech.kayys.aljabr.engine.verification.TestFailureSummary;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface TestIntelligenceService {
    Uni<List<TestDescriptor>> findTestsForSymbol(String symbolId, RepositoryGraph graph);
    Uni<List<TestDescriptor>> findTestsForFile(Path file, RepositoryGraph graph);
    Uni<List<TestDescriptor>> selectImpactedTests(ImpactReport report, RepositoryGraph graph);
    Uni<Map<String, String>> generateUnitTest(String targetClass, String testMethod, RepositoryGraph graph);
    Uni<String> repairFailingTest(TestFailureSummary failure, RepositoryGraph graph);
}
