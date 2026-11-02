package tech.kayys.cifr.engine.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.index.DefaultFileIndex;
import tech.kayys.cifr.engine.index.dependency.DefaultDependencyIndex;
import tech.kayys.cifr.engine.index.relation.DefaultRelationIndex;
import tech.kayys.cifr.engine.index.symbol.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ContextEngineTest {

    private Path tempWorkspace;
    private DefaultSymbolIndex symbolIndex;
    private DefaultFileIndex fileIndex;
    private DefaultDependencyIndex dependencyIndex;
    private DefaultRelationIndex relationIndex;
    private ContextBudgeter budgeter;
    private ContextEngine contextEngine;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkspace = Files.createTempDirectory("context-engine-test-");
        symbolIndex = new DefaultSymbolIndex();
        fileIndex = new DefaultFileIndex();
        dependencyIndex = new DefaultDependencyIndex();
        relationIndex = new DefaultRelationIndex();
        budgeter = new DefaultContextBudgeter();

        contextEngine = new DefaultContextEngine(symbolIndex, fileIndex, dependencyIndex, relationIndex, budgeter);
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
    void testContextBudgeterTruncation() {
        String longText = "line of code with lots of tokens\n".repeat(50);
        ContextSection longSection = ContextSection.of("LARGE CODE", longText, 50);

        ContextSection budgeted = budgeter.budgetSection(longSection, 50);
        assertNotNull(budgeted);
        assertTrue(budgeted.estimatedTokens() <= 60);
        assertTrue(budgeted.content().contains("[truncated to fit token budget]"));
    }

    @Test
    void testContextEngineAssembly() throws IOException {
        Path authFile = tempWorkspace.resolve("AuthService.java");
        Files.writeString(authFile,
            "package tech.kayys.auth;\n\n" +
            "public class AuthService {\n" +
            "    public boolean authenticate(String token) {\n" +
            "        if (token == null) return false;\n" +
            "        return true;\n" +
            "    }\n" +
            "}\n");

        SymbolId symId = new SymbolId("AuthService", "type", "AuthService.java");
        SymbolInfo symInfo = new SymbolInfo(symId, "AuthService", SymbolKind.CLASS, Path.of("AuthService.java"),
            new SourceRange(3, 8, 0, 100), Optional.empty());
        symbolIndex.registerSymbol(symInfo);

        ContextPlan plan = new ContextPlan(
            "task-42",
            "Fix null pointer in authenticate method",
            Optional.of("AuthService"),
            Optional.of(Path.of("AuthService.java")),
            Optional.of("NullPointerException at line 5: token is null"),
            List.of(),
            ContextBudget.defaultBudget()
        );

        ContextPacket packet = contextEngine.assemble(plan, tempWorkspace, "proj-1").await().indefinitely();

        assertNotNull(packet);
        assertEquals("task-42", packet.taskId());
        assertTrue(packet.totalEstimatedTokens() > 0);

        String prompt = packet.toPrompt();
        assertTrue(prompt.contains("TASK OBJECTIVE"));
        assertTrue(prompt.contains("Fix null pointer in authenticate method"));
        assertTrue(prompt.contains("FAILURE / ERROR TRACE"));
        assertTrue(prompt.contains("NullPointerException"));
        assertTrue(prompt.contains("TARGET DECLARATION"));
        assertTrue(prompt.contains("AuthService"));
        assertTrue(prompt.contains("CODE SNIPPET"));
        assertTrue(prompt.contains("authenticate"));
    }

    @Test
    void testContextBudgetConstraint() {
        ContextPlan plan = new ContextPlan(
            "task-100",
            "Refactor everything",
            Optional.empty(),
            Optional.empty(),
            Optional.of("Very long error stack trace\n".repeat(100)),
            List.of(),
            new ContextBudget(150, 50, 50, 50, 50, 50)
        );

        ContextPacket packet = contextEngine.assemble(plan, tempWorkspace, "proj-1").await().indefinitely();

        assertNotNull(packet);
        assertTrue(packet.totalEstimatedTokens() <= 200, "Should fit inside tight budget");
    }
}
