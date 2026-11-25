package tech.kayys.cifr.engine.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.kayys.cifr.engine.orchestrator.toolchain.BuildSystem;
import tech.kayys.cifr.engine.orchestrator.toolchain.ProjectDetector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolchainVerificationPlannerTest {

    @TempDir
    Path temporaryDirectory;

    private final ToolchainVerificationPlanner planner =
            new ToolchainVerificationPlanner(new ProjectDetector());

    @Test
    void plansMavenCompileAndTestWithoutShellCommands() throws Exception {
        Path root = workspace("maven");
        Files.writeString(root.resolve("pom.xml"), "<project/>");

        VerificationPlan plan = planner.plan(root);

        assertEquals(BuildSystem.MAVEN, plan.buildSystem());
        assertTrue(plan.supported());
        assertEquals(List.of("compile", "tests"), plan.requiredCheckIds());
        assertEquals(List.of("mvn", "-B", "-DskipTests", "compile"),
                plan.checks().get(0).command());
        assertEquals(List.of("mvn", "-B", "test"), plan.checks().get(1).command());
        assertTrue(plan.checks().stream().noneMatch(check ->
                check.command().stream().anyMatch(argument -> argument.contains("&&") || argument.contains(";"))));
    }

    @Test
    void prefersMavenWrapperWhenPresent() throws Exception {
        Path root = workspace("maven-wrapper");
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(root.resolve("mvnw"), "#!/bin/sh\nexit 0\n");

        VerificationPlan plan = planner.plan(root);

        assertEquals("./mvnw", plan.checks().get(0).command().get(0));
    }

    @Test
    void plansGradleCompileAndTests() throws Exception {
        Path root = workspace("gradle");
        Files.writeString(root.resolve("build.gradle.kts"), "plugins {}");

        VerificationPlan plan = planner.plan(root);

        assertEquals(BuildSystem.GRADLE, plan.buildSystem());
        assertEquals(List.of("gradle", "classes"), plan.checks().get(0).command());
        assertEquals(List.of("gradle", "test"), plan.checks().get(1).command());
    }

    @Test
    void plansNodeBuildAndTestsExplicitly() throws Exception {
        Path root = workspace("node");
        Files.writeString(root.resolve("package.json"), "{\"name\":\"sample\",\"scripts\":{\"build\":\"tsc\",\"test\":\"vitest run\"}}");

        VerificationPlan plan = planner.plan(root);

        assertEquals(BuildSystem.NPM, plan.buildSystem());
        assertEquals(List.of("npm", "run", "build"), plan.checks().get(0).command());
        assertEquals(List.of("npm", "test"), plan.checks().get(1).command());
    }

    @Test
    void doesNotInventNodeChecksWhenScriptsAreMissing() throws Exception {
        Path root = workspace("node-no-scripts");
        Files.writeString(root.resolve("package.json"), "{\"name\":\"sample\"}");

        VerificationPlan plan = planner.plan(root);

        assertEquals(BuildSystem.NPM, plan.buildSystem());
        assertFalse(plan.supported());
        assertTrue(plan.requiredCheckIds().isEmpty());
        assertTrue(plan.unsupportedReason().contains("NPM"));
    }

    @Test
    void plansOnlyNodeScriptsThatActuallyExist() throws Exception {
        Path root = workspace("node-test-only");
        Files.writeString(root.resolve("package.json"),
                "{\"name\":\"sample\",\"scripts\":{\"test\":\"vitest run\"}}");

        VerificationPlan plan = planner.plan(root);

        assertTrue(plan.supported());
        assertEquals(List.of("tests"), plan.requiredCheckIds());
        assertEquals(List.of("npm", "test"), plan.checks().get(0).command());
    }

    @Test
    void malformedNodeManifestProducesUnsupportedPlan() throws Exception {
        Path root = workspace("node-invalid");
        Files.writeString(root.resolve("package.json"), "{ not-json");

        VerificationPlan plan = planner.plan(root);

        assertFalse(plan.supported());
        assertTrue(plan.requiredCheckIds().isEmpty());
    }

    @Test
    void unknownBuildSystemDoesNotInventSuccessfulChecks() throws Exception {
        Path root = workspace("unknown");

        VerificationPlan plan = planner.plan(root);

        assertEquals(BuildSystem.UNKNOWN, plan.buildSystem());
        assertFalse(plan.supported());
        assertTrue(plan.requiredCheckIds().isEmpty());
        assertFalse(plan.unsupportedReason().isBlank());
    }

    @Test
    void rejectsMissingWorkspace() {
        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(temporaryDirectory.resolve("does-not-exist")));
    }

    private Path workspace(String name) throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve(name));
        return root;
    }
}
