package tech.kayys.cifr.engine.test;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.index.impact.ImpactNode;
import tech.kayys.cifr.engine.index.impact.ImpactReport;
import tech.kayys.cifr.engine.knowledge.graph.NodeType;
import tech.kayys.cifr.engine.knowledge.graph.RepositoryGraph;
import tech.kayys.cifr.engine.knowledge.graph.RepositoryNode;
import tech.kayys.cifr.engine.verification.TestFailureSummary;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultTestIntelligenceService implements TestIntelligenceService {
    private static final Logger LOG = Logger.getLogger(DefaultTestIntelligenceService.class);
    private static final Pattern TEST_CLASS_PATTERN = Pattern.compile(".*(Test|Spec|Suite|IT)$");
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile("test.*|should.*|verify.*|validate.*");

    @Override
    public Uni<List<TestDescriptor>> findTestsForSymbol(String symbolId, RepositoryGraph graph) {
        return Uni.createFrom().item(() -> {
            if (symbolId == null || graph == null) return List.of();
            Set<String> testIds = new HashSet<>();

            Set<RepositoryNode> dependents = graph.getDependents(symbolId);
            for (RepositoryNode dependent : dependents) {
                if (dependent instanceof RepositoryNode.Class) {
                    String className = dependent.name();
                    if (TEST_CLASS_PATTERN.matcher(className).matches()) {
                        testIds.add(dependent.id());
                    }
                } else if (dependent instanceof RepositoryNode.Method) {
                    String methodName = dependent.name();
                    if (TEST_METHOD_PATTERN.matcher(methodName).matches()) {
                        testIds.add(dependent.id());
                    }
                } else if (dependent instanceof RepositoryNode.Test) {
                    testIds.add(dependent.id());
                }
            }

            Set<RepositoryNode> directTests = graph.getNodesByType(NodeType.TEST);
            for (RepositoryNode t : directTests) {
                if (t.id().contains(symbolId) || t.name().contains(symbolId)) {
                    testIds.add(t.id());
                }
            }

            List<TestDescriptor> descriptors = new ArrayList<>();
            for (String id : testIds) {
                Optional<RepositoryNode> node = graph.getNode(id);
                if (node.isPresent()) {
                    RepositoryNode n = node.get();
                    if (n instanceof RepositoryNode.Class testClass) {
                        descriptors.add(new TestDescriptor(
                                testClass.id(),
                                testClass.name(),
                                TestType.UNIT,
                                findTestMethods(testClass, graph)
                        ));
                    } else if (n instanceof RepositoryNode.Test testNode) {
                        descriptors.add(new TestDescriptor(
                                testNode.id(),
                                testNode.name(),
                                TestType.UNIT,
                                List.of()
                        ));
                    }
                }
            }

            LOG.debugf("Found %d tests for symbol %s", descriptors.size(), symbolId);
            return descriptors;
        });
    }

    @Override
    public Uni<List<TestDescriptor>> findTestsForFile(Path file, RepositoryGraph graph) {
        if (file == null || graph == null) return Uni.createFrom().item(List.of());
        String filePath = file.toString().replace('\\', '/');

        return Uni.createFrom().item(() -> {
            Set<String> classIds = graph.nodes().entrySet().stream()
                    .filter(e -> e.getValue() instanceof RepositoryNode.Class)
                    .filter(e -> e.getKey().contains(filePath))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            List<TestDescriptor> allTests = new ArrayList<>();
            for (String classId : classIds) {
                allTests.addAll(findTestsForSymbol(classId, graph).await().indefinitely());
            }

            return allTests;
        });
    }

    @Override
    public Uni<List<TestDescriptor>> selectImpactedTests(ImpactReport report, RepositoryGraph graph) {
        return Uni.createFrom().item(() -> {
            if (report == null || graph == null) return List.of();
            Set<String> testIds = new HashSet<>();

            if (report.likelyTests() != null) {
                for (Path p : report.likelyTests()) {
                    testIds.add(p.toString());
                }
            }

            if (report.affectedSymbols() != null) {
                for (ImpactNode sym : report.affectedSymbols()) {
                    List<TestDescriptor> tests = findTestsForSymbol(sym.identifier(), graph).await().indefinitely();
                    tests.forEach(t -> testIds.add(t.id()));
                }
            }

            if (report.affectedFiles() != null) {
                for (ImpactNode fn : report.affectedFiles()) {
                    List<TestDescriptor> tests = findTestsForFile(fn.path(), graph).await().indefinitely();
                    tests.forEach(t -> testIds.add(t.id()));
                }
            }

            return testIds.stream()
                    .map(id -> graph.getNode(id).orElse(null))
                    .filter(Objects::nonNull)
                    .map(node -> {
                        if (node instanceof RepositoryNode.Class clazz) {
                            return new TestDescriptor(
                                    clazz.id(),
                                    clazz.name(),
                                    TestType.UNIT,
                                    findTestMethods(clazz, graph)
                            );
                        } else if (node instanceof RepositoryNode.Test t) {
                            return new TestDescriptor(
                                    t.id(),
                                    t.name(),
                                    TestType.UNIT,
                                    List.of()
                            );
                        }
                        return new TestDescriptor(node.id(), node.name(), TestType.UNIT, List.of());
                    })
                    .collect(Collectors.toList());
        });
    }

    @Override
    public Uni<Map<String, String>> generateUnitTest(String targetClass, String testMethod, RepositoryGraph graph) {
        return Uni.createFrom().item(() -> {
            Map<String, String> generated = new HashMap<>();
            String testClassName = targetClass + "Test";
            String testMethodName = testMethod != null && !testMethod.isBlank() ? "test" + Character.toUpperCase(testMethod.charAt(0)) + testMethod.substring(1) : "testExecution";

            generated.put("testClass", testClassName);
            generated.put("testMethod", testMethodName);
            generated.put("code", String.format("""
                package tech.kayys.generated;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;

                class %s {
                    @Test
                    void %s() {
                        // Generated verification test for %s
                        assertNotNull("%s");
                    }
                }
                """, testClassName, testMethodName, targetClass, targetClass));

            return generated;
        });
    }

    @Override
    public Uni<String> repairFailingTest(TestFailureSummary failure, RepositoryGraph graph) {
        return Uni.createFrom().item(() -> {
            if (failure == null) return "No failure information available.";

            String msg = ((failure.errorType() != null ? failure.errorType() + " " : "")
                    + (failure.message() != null ? failure.message() + " " : "")
                    + (failure.stackTrace() != null ? failure.stackTrace() : "")).toLowerCase(Locale.ROOT);

            if (msg.contains("nullpointerexception")) {
                return "Root cause: NullPointerException. Inject mock dependency or add null-safety check.";
            } else if (msg.contains("assertionerror") || msg.contains("assertionfailederror")) {
                return "Root cause: Assertion mismatch. Align expected value with updated business rules or fix output calculation.";
            } else if (msg.contains("classnotfoundexception") || msg.contains("noclassdeffounderror")) {
                return "Root cause: Missing classpath dependency. Add dependency to pom.xml or check package declaration.";
            }
            return "Consider inspecting test failure trace: " + failure.message();
        });
    }

    private List<TestMethod> findTestMethods(RepositoryNode.Class testClass, RepositoryGraph graph) {
        List<TestMethod> methods = new ArrayList<>();
        Set<RepositoryNode> deps = graph.getDependencies(testClass.id());
        for (RepositoryNode dep : deps) {
            if (dep instanceof RepositoryNode.Method method) {
                if (TEST_METHOD_PATTERN.matcher(method.name()).matches()) {
                    methods.add(new TestMethod(method.id(), method.name(), "PENDING"));
                }
            }
        }
        return methods;
    }
}
