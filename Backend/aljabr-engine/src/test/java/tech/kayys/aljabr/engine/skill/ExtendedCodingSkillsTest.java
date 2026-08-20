package tech.kayys.aljabr.engine.skill;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.skill.impl.CodeDebugSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeEditSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeReviewSkill;
import tech.kayys.aljabr.engine.skill.impl.CodeTestSkill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExtendedCodingSkillsTest {

    private Path tempWorkspace;
    private SkillRegistry skillRegistry;
    private SkillRouter skillRouter;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkspace = Files.createTempDirectory("extended-skills-test-");

        skillRegistry = new DefaultSkillRegistry();
        skillRegistry.register(new CodeEditSkill());
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
    void testCodeEditSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", tempWorkspace, Map.of(
            "instruction", "Add null check"
        ));

        SkillResult result = skillRouter.dispatch("code.edit", ctx).await().indefinitely();
        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Edit proposal generated"));
    }

    @Test
    void testCodeTestSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", tempWorkspace, Map.of(
            "target", "AuthServiceTest"
        ));

        SkillResult result = skillRouter.dispatch("code.test", ctx).await().indefinitely();
        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Test run completed"));
    }

    @Test
    void testCodeReviewSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", tempWorkspace, Map.of(
            "diff", "+ boolean isValid = true;"
        ));

        SkillResult result = skillRouter.dispatch("code.review", ctx).await().indefinitely();
        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Review passed"));
    }

    @Test
    void testCodeDebugSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", tempWorkspace, Map.of(
            "failureId", "fail-42"
        ));

        SkillResult result = skillRouter.dispatch("code.debug", ctx).await().indefinitely();
        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Debug session completed"));
    }
}
