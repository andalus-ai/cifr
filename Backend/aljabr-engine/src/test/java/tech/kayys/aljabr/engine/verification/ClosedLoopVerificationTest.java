package tech.kayys.aljabr.engine.verification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.context.ContextBudget;
import tech.kayys.aljabr.engine.context.ContextEngine;
import tech.kayys.aljabr.engine.context.ContextPlan;
import tech.kayys.aljabr.engine.context.DefaultContextBudgeter;
import tech.kayys.aljabr.engine.context.DefaultContextEngine;
import tech.kayys.aljabr.engine.index.DefaultFileIndex;
import tech.kayys.aljabr.engine.index.WorkspaceFingerprint;
import tech.kayys.aljabr.engine.index.dependency.DefaultDependencyIndex;
import tech.kayys.aljabr.engine.index.indexer.DefaultWorkspaceIndexer;
import tech.kayys.aljabr.engine.index.indexer.WorkspaceIndexer;
import tech.kayys.aljabr.engine.index.relation.DefaultRelationIndex;
import tech.kayys.aljabr.engine.index.symbol.DefaultSymbolIndex;
import tech.kayys.aljabr.engine.patch.mutation.*;
import tech.kayys.aljabr.engine.repair.ClosedLoopCoder;
import tech.kayys.aljabr.engine.repair.DefaultClosedLoopCoder;
import tech.kayys.aljabr.engine.repair.RepairResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ClosedLoopVerificationTest {

    private Path tempWorkspace;
    private VerificationEngine verificationEngine;
    private MutationEngine mutationEngine;
    private ContextEngine contextEngine;
    private WorkspaceIndexer workspaceIndexer;
    private DefaultFileIndex fileIndex;
    private ClosedLoopCoder closedLoopCoder;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkspace = Files.createTempDirectory("closed-loop-test-");

        DefaultRelationIndex relationIndex = new DefaultRelationIndex();
        DefaultSymbolIndex symbolIndex = new DefaultSymbolIndex();
        fileIndex = new DefaultFileIndex();
        DefaultDependencyIndex dependencyIndex = new DefaultDependencyIndex();

        verificationEngine = new DefaultVerificationEngine(relationIndex);
        mutationEngine = new DefaultMutationEngine();
        contextEngine = new DefaultContextEngine(symbolIndex, fileIndex, dependencyIndex, relationIndex, new DefaultContextBudgeter());
        workspaceIndexer = new DefaultWorkspaceIndexer(fileIndex, symbolIndex, dependencyIndex, relationIndex);

        closedLoopCoder = new DefaultClosedLoopCoder(contextEngine, mutationEngine, verificationEngine, workspaceIndexer, fileIndex);
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
    void testVerificationEngineLevel0Syntax() {
        EditProposal empty = new EditProposal("Empty", List.of(), List.of(), List.of());
        VerificationResult res = verificationEngine.verify(new WorkspaceFingerprint("fp", "1.0", "cfg"), empty, VerificationLevel.SYNTAX_L0, tempWorkspace).await().indefinitely();

        assertFalse(res.success());
        assertEquals(VerificationLevel.SYNTAX_L0, res.level());

        FileEdit validEdit = new FileEdit(Path.of("src/App.java"), List.of(new TextEdit(1, 1, "a", "b")));
        EditProposal valid = new EditProposal("Valid", List.of(validEdit), List.of(), List.of());
        VerificationResult validRes = verificationEngine.verify(new WorkspaceFingerprint("fp", "1.0", "cfg"), valid, VerificationLevel.SYNTAX_L0, tempWorkspace).await().indefinitely();

        assertTrue(validRes.success());
    }

    @Test
    void testVerificationEngineLevel1StaticAnalysisQualityGate() throws IOException {
        Path src = tempWorkspace.resolve("src");
        Files.createDirectories(src);

        Path vulnerableFile = src.resolve("InsecureAuth.java");
        Files.writeString(vulnerableFile, "public class InsecureAuth {\n    // cwe-89 sql injection\n    void query() {}\n}");

        FileEdit edit = new FileEdit(Path.of("src/InsecureAuth.java"), List.of(new TextEdit(1, 1, "void query", "void query")));
        EditProposal proposal = new EditProposal("Test Sonar gate", List.of(edit), List.of(), List.of());

        VerificationResult res = verificationEngine.verify(new WorkspaceFingerprint("fp", "1.0", "cfg"), proposal, VerificationLevel.LINT_L1, tempWorkspace).await().indefinitely();

        assertFalse(res.success());
        assertEquals(VerificationLevel.LINT_L1, res.level());
        assertEquals(1, res.failures().size());
        assertEquals("SecurityVulnerability", res.failures().get(0).errorType());
    }

    @Test
    void testVerificationEngineTargetedTestFailures() throws IOException {
        Path src = tempWorkspace.resolve("src");
        Files.createDirectories(src);

        Path brokenFile = src.resolve("BrokenService.java");
        Files.writeString(brokenFile, "public class BrokenService { void run() { throw new RuntimeException(\"broken\"); } }");

        FileEdit edit = new FileEdit(Path.of("src/BrokenService.java"), List.of(new TextEdit(1, 1, "void run", "void run")));
        EditProposal proposal = new EditProposal("Testing broken file", List.of(edit), List.of(), List.of());

        VerificationResult res = verificationEngine.verify(new WorkspaceFingerprint("fp", "1.0", "cfg"), proposal, VerificationLevel.TARGETED_TESTS_L2, tempWorkspace).await().indefinitely();

        assertFalse(res.success());
        assertFalse(res.failures().isEmpty());
        assertEquals("AssertionError", res.failures().get(0).errorType());
    }

    @Test
    void testClosedLoopExecutionSuccess() throws IOException {
        Path src = tempWorkspace.resolve("src");
        Files.createDirectories(src);

        Path target = src.resolve("Main.java");
        Files.writeString(target, "// original content\npublic class Main {}\n");

        ContextPlan plan = new ContextPlan(
            "task-repair-1",
            "Ensure Main is well formed",
            Optional.of("Main"),
            Optional.of(Path.of("src/Main.java")),
            Optional.empty(),
            List.of(),
            ContextBudget.defaultBudget()
        );

        RepairResult result = closedLoopCoder.executeLoop(plan, 3, tempWorkspace, "proj-1").await().indefinitely();

        assertTrue(result.success());
        assertEquals(1, result.totalAttempts());
        assertNotNull(result.finalFingerprint());
    }
}
