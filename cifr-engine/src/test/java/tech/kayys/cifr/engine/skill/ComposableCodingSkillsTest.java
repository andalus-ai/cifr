package tech.kayys.cifr.engine.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.skill.impl.CodeExplainSkill;
import tech.kayys.cifr.engine.skill.impl.CodeInspectSkill;
import tech.kayys.cifr.engine.skill.impl.CodeSearchSkill;
import tech.kayys.cifr.engine.skill.impl.CodeTraceSkill;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ComposableCodingSkillsTest {

    private SkillRegistry skillRegistry;
    private SkillRouter skillRouter;

    @BeforeEach
    void setUp() {
        skillRegistry = new DefaultSkillRegistry();
        skillRegistry.register(new CodeSearchSkill());
        skillRegistry.register(new CodeInspectSkill());
        skillRegistry.register(new CodeTraceSkill());
        skillRegistry.register(new CodeExplainSkill());

        skillRouter = new SkillRouter(skillRegistry);
    }

    @Test
    void testSkillRegistryListingAndFinding() {
        List<SkillDescriptor> descriptors = skillRegistry.listDescriptors();
        assertEquals(4, descriptors.size());

        assertTrue(skillRegistry.find("code.search").isPresent());
        assertEquals(SkillCategory.DISCOVERY, skillRegistry.find("code.search").get().descriptor().category());
        assertEquals(ReasoningMode.NONE, skillRegistry.find("code.search").get().descriptor().reasoningMode());

        assertTrue(skillRegistry.find("code.inspect").isPresent());
        assertEquals(SkillCategory.DISCOVERY, skillRegistry.find("code.inspect").get().descriptor().category());
        assertEquals(ReasoningMode.OPTIONAL, skillRegistry.find("code.inspect").get().descriptor().reasoningMode());

        assertTrue(skillRegistry.find("code.trace").isPresent());
        assertEquals(SkillCategory.UNDERSTANDING, skillRegistry.find("code.trace").get().descriptor().category());
        assertEquals(ReasoningMode.NONE, skillRegistry.find("code.trace").get().descriptor().reasoningMode());

        assertTrue(skillRegistry.find("code.explain").isPresent());
        assertEquals(SkillCategory.UNDERSTANDING, skillRegistry.find("code.explain").get().descriptor().category());
        assertEquals(ReasoningMode.REQUIRED, skillRegistry.find("code.explain").get().descriptor().reasoningMode());
    }

    @Test
    void testCodeSearchSkillExecution() {
        SkillContext ctx = new SkillContext("task-1", "exec-1", Path.of("."), Map.of("query", "AuthService"));
        SkillResult result = skillRouter.dispatch("code.search", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertTrue(result.summary().contains("Search completed"));
    }
}
