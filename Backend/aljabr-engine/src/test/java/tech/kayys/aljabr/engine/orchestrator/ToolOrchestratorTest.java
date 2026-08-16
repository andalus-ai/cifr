package tech.kayys.aljabr.engine.orchestrator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.orchestrator.adapter.*;
import tech.kayys.aljabr.engine.orchestrator.exec.DefaultExecutionEngine;
import tech.kayys.aljabr.engine.orchestrator.exec.ExecutionEngine;
import tech.kayys.aljabr.engine.orchestrator.exec.ExecutionResult;
import tech.kayys.aljabr.engine.orchestrator.output.*;
import tech.kayys.aljabr.engine.orchestrator.policy.DefaultToolPolicy;
import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;
import tech.kayys.aljabr.engine.orchestrator.policy.ToolPolicy;
import tech.kayys.aljabr.engine.orchestrator.policy.ToolPolicyDecision;
import tech.kayys.aljabr.engine.orchestrator.toolchain.BuildSystem;
import tech.kayys.aljabr.engine.orchestrator.toolchain.DefaultProjectDetector;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectDetector;
import tech.kayys.aljabr.engine.orchestrator.toolchain.ProjectToolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ToolOrchestratorTest {

    private Path tempWorkspace;
    private ProjectDetector projectDetector;
    private DefaultToolResolver toolResolver;
    private ToolPolicy toolPolicy;
    private OutputProcessor outputProcessor;
    private ExecutionEngine executionEngine;
    private ToolOrchestrator orchestrator;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkspace = Files.createTempDirectory("orchestrator-test-");

        projectDetector = new DefaultProjectDetector();
        toolResolver = new DefaultToolResolver(List.of(
            new MavenToolAdapter(),
            new GradleToolAdapter(),
            new FlutterToolAdapter(),
            new CMakeToolAdapter(),
            new MakeToolAdapter(),
            new SwiftPmToolAdapter(),
            new XcodeToolAdapter(),
            new SonarToolAdapter()
        ));
        toolPolicy = new DefaultToolPolicy();
        outputProcessor = new DefaultOutputProcessor();
        executionEngine = new DefaultExecutionEngine(outputProcessor);

        orchestrator = new DefaultToolOrchestrator(projectDetector, toolResolver, toolPolicy, executionEngine);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempWorkspace != null && Files.exists(tempWorkspace)) {
            try (var stream = Files.walk(tempWorkspace)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void testProjectDetectorToolchains() throws IOException {
        // 1. Maven
        Files.writeString(tempWorkspace.resolve("pom.xml"), "<project></project>");
        ProjectToolchain mavenTc = projectDetector.detect(tempWorkspace).await().indefinitely();
        assertEquals(BuildSystem.MAVEN, mavenTc.buildSystem());
        assertTrue(mavenTc.languages().contains("Java"));

        // 2. Flutter
        Files.deleteIfExists(tempWorkspace.resolve("pom.xml"));
        Files.writeString(tempWorkspace.resolve("pubspec.yaml"), "name: test_app");
        ProjectToolchain flutterTc = projectDetector.detect(tempWorkspace).await().indefinitely();
        assertEquals(BuildSystem.FLUTTER, flutterTc.buildSystem());
        assertTrue(flutterTc.languages().contains("Dart"));

        // 3. CMake (C/C++)
        Files.deleteIfExists(tempWorkspace.resolve("pubspec.yaml"));
        Files.writeString(tempWorkspace.resolve("CMakeLists.txt"), "cmake_minimum_required(VERSION 3.20)");
        ProjectToolchain cmakeTc = projectDetector.detect(tempWorkspace).await().indefinitely();
        assertEquals(BuildSystem.CMAKE, cmakeTc.buildSystem());
        assertTrue(cmakeTc.languages().contains("C++"));

        // 4. Swift Package Manager
        Files.deleteIfExists(tempWorkspace.resolve("CMakeLists.txt"));
        Files.writeString(tempWorkspace.resolve("Package.swift"), "// swift-tools-version:5.9");
        ProjectToolchain swiftTc = projectDetector.detect(tempWorkspace).await().indefinitely();
        assertEquals(BuildSystem.SWIFT_PM, swiftTc.buildSystem());
        assertTrue(swiftTc.languages().contains("Swift"));

        // 5. Xcode (Swift / Objective-C)
        Files.deleteIfExists(tempWorkspace.resolve("Package.swift"));
        Files.createDirectories(tempWorkspace.resolve("App.xcodeproj"));
        ProjectToolchain xcodeTc = projectDetector.detect(tempWorkspace).await().indefinitely();
        assertEquals(BuildSystem.XCODE, xcodeTc.buildSystem());
        assertTrue(xcodeTc.languages().contains("Objective-C"));
    }

    @Test
    void testToolResolution() throws IOException {
        Files.writeString(tempWorkspace.resolve("pom.xml"), "<project></project>");
        ProjectToolchain tc = projectDetector.detect(tempWorkspace).await().indefinitely();

        ToolIntent buildIntent = ToolIntent.of("intent-1", "task-1", ToolKind.BUILD, "Compile project", Map.of());
        ResolvedTool resolvedBuild = toolResolver.resolve(buildIntent, tc).await().indefinitely();
        assertEquals(List.of("mvn", "compile", "-DskipTests"), resolvedBuild.command());

        ToolIntent testIntent = ToolIntent.of("intent-2", "task-1", ToolKind.RUN_TEST, "Run specific test", Map.of("test", "AuthTest#login"));
        ResolvedTool resolvedTest = toolResolver.resolve(testIntent, tc).await().indefinitely();
        assertEquals(List.of("mvn", "test", "-Dtest=AuthTest#login"), resolvedTest.command());
    }

    @Test
    void testCppToolResolution() throws IOException {
        Files.writeString(tempWorkspace.resolve("CMakeLists.txt"), "project(MyCppProject)");
        ProjectToolchain tc = projectDetector.detect(tempWorkspace).await().indefinitely();

        ToolIntent buildIntent = ToolIntent.of("intent-c-build", "task-1", ToolKind.BUILD, "Build C++ project", Map.of());
        ResolvedTool resolvedBuild = toolResolver.resolve(buildIntent, tc).await().indefinitely();
        assertEquals(List.of("cmake", "--build", "build"), resolvedBuild.command());

        ToolIntent testIntent = ToolIntent.of("intent-c-test", "task-1", ToolKind.RUN_TEST, "Run ctest target", Map.of("test", "test_engine"));
        ResolvedTool resolvedTest = toolResolver.resolve(testIntent, tc).await().indefinitely();
        assertEquals(List.of("ctest", "--test-dir", "build", "--output-on-failure", "-R", "test_engine"), resolvedTest.command());
    }

    @Test
    void testSwiftAndXcodeToolResolution() throws IOException {
        // Swift PM
        Files.writeString(tempWorkspace.resolve("Package.swift"), "// swift-tools-version:5.9");
        ProjectToolchain swiftTc = projectDetector.detect(tempWorkspace).await().indefinitely();

        ToolIntent swiftBuild = ToolIntent.of("intent-swift-build", "task-1", ToolKind.BUILD, "Build Swift package", Map.of());
        ResolvedTool resolvedSwiftBuild = toolResolver.resolve(swiftBuild, swiftTc).await().indefinitely();
        assertEquals(List.of("swift", "build"), resolvedSwiftBuild.command());

        ToolIntent swiftTest = ToolIntent.of("intent-swift-test", "task-1", ToolKind.RUN_TEST, "Run Swift test", Map.of("test", "AuthTests.testLogin"));
        ResolvedTool resolvedSwiftTest = toolResolver.resolve(swiftTest, swiftTc).await().indefinitely();
        assertEquals(List.of("swift", "test", "--filter", "AuthTests.testLogin"), resolvedSwiftTest.command());

        // Xcode / Objective-C
        Files.deleteIfExists(tempWorkspace.resolve("Package.swift"));
        Files.createDirectories(tempWorkspace.resolve("App.xcodeproj"));
        ProjectToolchain xcodeTc = projectDetector.detect(tempWorkspace).await().indefinitely();

        ToolIntent objcFormat = ToolIntent.of("intent-objc-format", "task-1", ToolKind.FORMAT, "Format ObjC", Map.of("file", "Sources/Main.m"));
        ResolvedTool resolvedObjcFormat = toolResolver.resolve(objcFormat, xcodeTc).await().indefinitely();
        assertEquals(List.of("clang-format", "-i", "Sources/Main.m"), resolvedObjcFormat.command());
    }

    @Test
    void testToolPolicySafetyGate() {
        ResolvedTool dangerousTool = new ResolvedTool(
            ToolKind.RUN_COMMAND,
            List.of("sudo", "rm", "-rf", "/"),
            tempWorkspace,
            ExecutionEnvironment.defaultSandboxed()
        );

        ToolIntent intent = ToolIntent.of("intent-danger", "task-1", ToolKind.RUN_COMMAND, "Root wipe", Map.of());
        ToolPolicyDecision decision = toolPolicy.evaluate(intent, dangerousTool, tempWorkspace).await().indefinitely();

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("Disallowed"));
    }

    @Test
    void testOutputProcessorDiagnostics() {
        String mockLog = "[ERROR] /src/main/java/AuthService.java:[42,15] cannot find symbol\n" +
                         "[ERROR]   symbol:   class JwtVerifier\n" +
                         "[ERROR]   location: class AuthService";

        ProcessedOutput processed = outputProcessor.process(1, "", mockLog, 4096).await().indefinitely();

        assertEquals(ExecutionFailureType.BUILD_FAILURE, processed.failureType());
        assertFalse(processed.diagnostics().isEmpty());
        assertEquals(42, processed.diagnostics().get(0).line());
        assertEquals("COMPILER", processed.diagnostics().get(0).category());
    }

    @Test
    void testCppClangCompilerDiagnostics() {
        String clangLog = "src/engine/tensor.cpp:24:10: error: use of undeclared identifier 'safetensor_read'\n" +
                          "    auto res = safetensor_read(fp);\n" +
                          "               ^\n" +
                          "src/engine/tensor.cpp:30:5: warning: unused variable 'flags' [-Wunused-variable]\n";

        ProcessedOutput processed = outputProcessor.process(1, "", clangLog, 4096).await().indefinitely();

        assertEquals(ExecutionFailureType.BUILD_FAILURE, processed.failureType());
        assertEquals(2, processed.diagnostics().size());

        ExecutionDiagnostic errDiag = processed.diagnostics().get(0);
        assertEquals(24, errDiag.line());
        assertEquals("CLANG_GCC", errDiag.category());
        assertTrue(errDiag.message().contains("undeclared identifier"));
    }

    @Test
    void testSwiftCompilerDiagnostics() {
        String swiftLog = "Sources/App/Main.swift:15:9: error: cannot find 'AuthToken' in scope\n" +
                          "    let token = AuthToken.generate()\n" +
                          "                ^\n";

        ProcessedOutput processed = outputProcessor.process(1, "", swiftLog, 4096).await().indefinitely();

        assertEquals(ExecutionFailureType.BUILD_FAILURE, processed.failureType());
        assertFalse(processed.diagnostics().isEmpty());

        ExecutionDiagnostic diag = processed.diagnostics().get(0);
        assertEquals(15, diag.line());
        assertEquals("SWIFTC", diag.category());
        assertTrue(diag.message().contains("cannot find 'AuthToken' in scope"));
    }

    @Test
    void testSonarToolResolution() throws IOException {
        Files.writeString(tempWorkspace.resolve("sonar-project.properties"), "sonar.projectKey=test-proj");
        ProjectToolchain tc = projectDetector.detect(tempWorkspace).await().indefinitely();

        ToolIntent sonarIntent = ToolIntent.of("intent-sonar", "task-1", ToolKind.STATIC_ANALYSIS, "Run Sonar analysis", Map.of("file", "src/AuthService.java"));
        ResolvedTool resolved = toolResolver.resolve(sonarIntent, tc).await().indefinitely();

        assertEquals("sonar-scanner", resolved.command().get(0));
        assertTrue(resolved.command().contains("-Dsonar.inclusions=src/AuthService.java"));
        assertTrue(resolved.command().contains("-Dsonar.sarif.output=sonar-report.sarif"));
    }

    @Test
    void testSarifOutputParsing() {
        String mockSarif = "{\n" +
            "  \"version\": \"2.1.0\",\n" +
            "  \"runs\": [{\n" +
            "    \"tool\": { \"driver\": { \"name\": \"SonarQube\" } },\n" +
            "    \"results\": [{\n" +
            "      \"ruleId\": \"java:S2259\",\n" +
            "      \"level\": \"error\",\n" +
            "      \"message\": { \"text\": \"'user' is dereferenced here but might be null\" },\n" +
            "      \"locations\": [{\n" +
            "        \"physicalLocation\": {\n" +
            "          \"artifactLocation\": { \"uri\": \"src/main/java/AuthService.java\" },\n" +
            "          \"region\": { \"startLine\": 42, \"startColumn\": 10 }\n" +
            "        }\n" +
            "      }]\n" +
            "    }]\n" +
            "  }]\n" +
            "}";

        ProcessedOutput processed = outputProcessor.process(1, mockSarif, "", 4096).await().indefinitely();

        assertEquals(ExecutionFailureType.BUILD_FAILURE, processed.failureType());
        assertEquals(1, processed.diagnostics().size());

        ExecutionDiagnostic diag = processed.diagnostics().get(0);
        assertEquals(42, diag.line());
        assertEquals("BUG", diag.category());
        assertTrue(diag.message().contains("java:S2259"));
        assertTrue(diag.message().contains("dereferenced"));
    }

    @Test
    void testOrchestratorExecutionFlow() {
        ToolIntent intent = ToolIntent.of("intent-echo", "task-1", ToolKind.RUN_COMMAND, "Print message", Map.of("command", "echo HelloAljabr"));
        ExecutionResult result = orchestrator.executeIntent(intent, tempWorkspace).await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(ExecutionStatus.SUCCESS, result.status());
        assertTrue(result.output().snippet().contains("HelloAljabr"));
    }
}
