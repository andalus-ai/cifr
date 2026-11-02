package tech.kayys.cifr.engine.agent;

import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.context.ContextPacket;
import tech.kayys.cifr.engine.context.ContextSection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptCompilerTest {

    @Test
    void testPromptCompilation() {
        DefaultPromptCompiler compiler = new DefaultPromptCompiler();

        AgentDefinition agent = AgentDefinition.of(
            AgentId.of("coder"),
            "Software Engineer",
            "Expert Java developer",
            AgentInstructions.of("Senior Backend Engineer", List.of("Write clean code", "Preserve tests")),
            List.of("debugging")
        );

        List<String> activeSkills = List.of("debugging: Reproduce defect, locate cause, patch minimally, verify");

        ContextSection taskSec = ContextSection.of("Task Context", "Fix null pointer in AuthenticationFilter.java", 10);
        ContextSection evSec = ContextSection.of("Observed Evidence", "NullPointerException at line 42", 5);
        ContextPacket packet = ContextPacket.of("task-101", List.of(taskSec, evSec));

        ModelRequest req = compiler.compile(agent, activeSkills, packet);

        assertNotNull(req);
        assertTrue(req.systemPrompt().contains("Senior Backend Engineer"));
        assertTrue(req.systemPrompt().contains("Write clean code"));
        assertTrue(req.systemPrompt().contains("debugging"));
        assertTrue(req.userPrompt().contains("Fix null pointer in AuthenticationFilter.java"));
        assertTrue(req.userPrompt().contains("NullPointerException at line 42"));
    }
}
