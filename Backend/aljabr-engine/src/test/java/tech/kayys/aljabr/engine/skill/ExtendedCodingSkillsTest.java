package tech.kayys.aljabr.engine.skill;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.patch.mutation.DefaultMutationEngine;
import tech.kayys.aljabr.engine.patch.mutation.MutationEngine;
import tech.kayys.aljabr.engine.skill.impl.CodeDebugSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeEditSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeReviewSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeTestSkill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExtendedCodingSkillsTest {

    private Path tempWorkspace;
    private SkillRegistry skillRegistry;
    private SkillRouter skillRouter;
    private MutationEngine mutationEngine;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkspace = Files.createTempDirectory("extended-skills-test-");
        mutationEngine = new DefaultMutationEngine();

        skillRegistry = new DefaultSkillRegistry();
        skillRegistry.register(new CodeEditSkill(mutationEngine));
        skillRegistry.register(new CodeTestSkill());
        skillRegistry.register(new CodeReviewSkill());
        skillRegistry.register(new CodeDebugSkill());

        skillRouter = new SkillRouter(skillRegistry);
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
    void testCodeEditSkillExecution() throws IOException {
        Path file = tempWorkspace.resolve("Greeter.java");
        Files.writeString(file, "public class Greeter { String msg = \"hi\"; }");

        SkillContext ctx = new SkillContext(
            "task-1", "exec-1", tempWorkspace,
            Map.of(
                "file", "Greeter.java",
                "expectedText", "String msg = \"hi\";",
                "replacementText", "String msg = \"hello world\";"
            )
        );

        SkillResult result = skillRouter.dispatch("code.edit", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Successfully applied mutation"));
        assertEquals("public class Greeter { String msg = \"hello world\"; }", Files.readString(file));
    }

    @Test
    void testCodeTestSkillExecution() {
        SkillContext ctxPass = new SkillContext("task-1", "exec-1", tempWorkspace, Map.of("testTarget", "GreeterTest"));
        SkillResult resultPass = skillRouter.dispatch("code.test", ctxPass).await().indefinitely();
        assertTrue(resultPass.isSuccess());
        assertEquals(true, resultPass.outputData().get("passed"));

        SkillContext ctxFail = new SkillContext("task-1", "exec-1", tempWorkspace, Map.of("testTarget", "FailingTest"));
        SkillResult resultFail = skillRouter.dispatch("code.test", ctxFail).await().indefinitely();
        assertFalse(resultFail.isSuccess());
        assertEquals(false, resultFail.outputData().get("passed"));
    }

    @Test
    void testCodeReviewSkillExecution() {
        SkillContext ctx = new SkillContext(
            "task-1", "exec-1", tempWorkspace,
            Map.of(
                "diff", "+ String msg = \"hello\";",
                "changedFiles", List.of("Greeter.java"),
                "invariants", List.of("Must not break API contract")
            )
        );

        SkillResult result = skillRouter.dispatch("code.review", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(true, result.outputData().get("requiresModel"));
        assertTrue(result.evidenceIds().contains("Greeter.java"));
    }

    @Test
    void testCodeDebugSkillExecution() {
        String errorLog = "Exception in thread \"main\" java.lang.NullPointerException\n" +
            "\tat tech.kayys.aljabr.Greeter.greet(Greeter.java:42)\n" +
            "\tat tech.kayys.aljabr.Main.main(Main.java:15)\n";

        SkillContext ctx = new SkillContext(
            "task-1", "exec-1", tempWorkspace,
            Map.of("errorLog", errorLog)
        );

        SkillResult result = skillRouter.dispatch("code.debug", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.evidenceIds().contains("Greeter.java"));
        assertTrue(result.evidenceIds().contains("Main.java"));
    }
}
