package tech.kayys.cifr.engine.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.architecture.ArchitectureAnalyzer;
import tech.kayys.cifr.engine.architecture.ArchitectureViolation;
import tech.kayys.cifr.engine.architecture.DefaultArchitectureAnalyzer;
import tech.kayys.cifr.engine.index.impact.ImpactConfidence;
import tech.kayys.cifr.engine.index.impact.ImpactNode;
import tech.kayys.cifr.engine.index.impact.ImpactReport;
import tech.kayys.cifr.engine.knowledge.graph.ClassKind;
import tech.kayys.cifr.engine.knowledge.graph.NodeType;
import tech.kayys.cifr.engine.knowledge.graph.RepositoryGraph;
import tech.kayys.cifr.engine.knowledge.graph.RepositoryNode;
import tech.kayys.cifr.engine.test.DefaultTestIntelligenceService;
import tech.kayys.cifr.engine.test.TestDescriptor;
import tech.kayys.cifr.engine.test.TestIntelligenceService;
import tech.kayys.cifr.engine.verification.TestFailureSummary;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryGraphAndImpactTest {

    private RepositoryGraph graph;
    private ArchitectureAnalyzer architectureAnalyzer;
    private TestIntelligenceService testIntelligenceService;

    @BeforeEach
    void setUp() {
        graph = new RepositoryGraph();
        architectureAnalyzer = new DefaultArchitectureAnalyzer();
        testIntelligenceService = new DefaultTestIntelligenceService();
    }

    @Test
    void testGraphNodeAdditionAndLookup() {
        RepositoryNode.Class domainClass = new RepositoryNode.Class(
                "tech.kayys.cifr.domain.User",
                "User",
                ClassKind.RECORD,
                Set.of(),
                Set.of(),
                Set.of()
        );

        RepositoryNode.Test testNode = new RepositoryNode.Test(
                "test:UserTest.java",
                "UserTest",
                "src/test/java/UserTest.java"
        );

        graph.addNode(domainClass);
        graph.addNode(testNode);

        assertEquals(2, graph.size());
        assertTrue(graph.getNode("tech.kayys.cifr.domain.User").isPresent());
        assertEquals(NodeType.CLASS, graph.getNode("tech.kayys.cifr.domain.User").get().type());

        Set<RepositoryNode> classes = graph.getNodesByType(NodeType.CLASS);
        assertEquals(1, classes.size());

        Set<RepositoryNode> searchResults = graph.searchByName("user");
        assertEquals(2, searchResults.size()); // "User" and "UserTest"
    }

    @Test
    void testBidirectionalEdgeTraversal() {
        String serviceId = "tech.kayys.cifr.application.UserService";
        String repoId = "tech.kayys.cifr.domain.UserRepository";

        graph.addNode(new RepositoryNode.Class(serviceId, "UserService", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));
        graph.addNode(new RepositoryNode.Class(repoId, "UserRepository", ClassKind.INTERFACE, Set.of(), Set.of(), Set.of()));

        graph.addEdge(serviceId, repoId); // service depends on repository

        Set<RepositoryNode> deps = graph.getDependencies(serviceId);
        assertEquals(1, deps.size());
        assertEquals(repoId, deps.iterator().next().id());

        Set<RepositoryNode> dependents = graph.getDependents(repoId);
        assertEquals(1, dependents.size());
        assertEquals(serviceId, dependents.iterator().next().id());
    }

    @Test
    void testTransitiveDependencyTraversal() {
        String a = "nodeA";
        String b = "nodeB";
        String c = "nodeC";
        String d = "nodeD";

        graph.addNode(new RepositoryNode.Class(a, "A", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));
        graph.addNode(new RepositoryNode.Class(b, "B", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));
        graph.addNode(new RepositoryNode.Class(c, "C", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));
        graph.addNode(new RepositoryNode.Class(d, "D", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));

        graph.addEdge(a, b);
        graph.addEdge(b, c);
        graph.addEdge(c, d);

        Set<RepositoryNode> depth1 = graph.getTransitiveDependencies(a, 1);
        assertEquals(1, depth1.size());

        Set<RepositoryNode> depth3 = graph.getTransitiveDependencies(a, 3);
        assertEquals(3, depth3.size());
    }

    @Test
    void testArchitectureViolationDetection() {
        String domainEntity = "tech.kayys.cifr.domain.Order";
        String infraDb = "tech.kayys.cifr.infrastructure.PostgresAdapter";

        graph.addNode(new RepositoryNode.Class(domainEntity, "Order", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));
        graph.addNode(new RepositoryNode.Class(infraDb, "PostgresAdapter", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));

        // Illegal edge: Domain directly calling Infrastructure
        graph.addEdge(domainEntity, infraDb);

        List<ArchitectureViolation> violations = architectureAnalyzer.detectViolations(graph, architectureAnalyzer.defaultRules())
                .await().atMost(Duration.ofSeconds(2));

        assertFalse(violations.isEmpty());
        assertEquals(domainEntity, violations.get(0).fromNodeId());
        assertEquals(infraDb, violations.get(0).toNodeId());
    }

    @Test
    void testTestDiscoveryAndRepair() {
        String targetSymbol = "tech.kayys.cifr.domain.PaymentService";
        String testSymbol = "tech.kayys.cifr.domain.PaymentServiceTest";

        graph.addNode(new RepositoryNode.Class(targetSymbol, "PaymentService", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));
        graph.addNode(new RepositoryNode.Class(testSymbol, "PaymentServiceTest", ClassKind.CLASS, Set.of(), Set.of(), Set.of()));
        graph.addEdge(testSymbol, targetSymbol); // test depends on service

        List<TestDescriptor> tests = testIntelligenceService.findTestsForSymbol(targetSymbol, graph)
                .await().atMost(Duration.ofSeconds(2));

        assertFalse(tests.isEmpty());
        assertEquals(testSymbol, tests.get(0).id());

        // Test generation
        Map<String, String> gen = testIntelligenceService.generateUnitTest("PaymentService", "processPayment", graph)
                .await().atMost(Duration.ofSeconds(2));
        assertNotNull(gen.get("code"));
        assertTrue(gen.get("code").contains("testProcessPayment"));

        // Test repair
        TestFailureSummary summary = TestFailureSummary.of(
                "PaymentFailure",
                "java.lang.NullPointerException: merchantId was null",
                "tech.kayys.cifr.domain.PaymentServiceTest#testProcess"
        );
        String repair = testIntelligenceService.repairFailingTest(summary, graph)
                .await().atMost(Duration.ofSeconds(2));
        assertTrue(repair.contains("NullPointerException"));
    }
}
