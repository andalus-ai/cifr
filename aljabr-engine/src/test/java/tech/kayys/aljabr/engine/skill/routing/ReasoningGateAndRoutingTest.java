package tech.kayys.aljabr.engine.skill.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.skill.*;
import tech.kayys.aljabr.engine.skill.impl.*;
import tech.kayys.aljabr.engine.skill.reasoning.ReasoningDecision;
import tech.kayys.aljabr.engine.skill.reasoning.ReasoningGate;
import tech.kayys.aljabr.engine.skill.telemetry.SkillTelemetryCollector;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ReasoningGateAndRoutingTest {

    private SkillRegistry skillRegistry;
    private ReasoningGate reasoningGate;
    private SkillSelector skillSelector;
    private SkillTelemetryCollector telemetryCollector;
    private SkillRouter skillRouter;

    @BeforeEach
    void setUp() {
        skillRegistry = new DefaultSkillRegistry();
        skillRegistry.register(new CodeSearchSkill());
        skillRegistry.register(new CodeInspectSkill());
        skillRegistry.register(new CodeTraceSkill());
        skillRegistry.register(new CodeExplainSkill());
        skillRegistry.register(new CodeTestSkill());
        skillRegistry.register(new CodeReviewSkill());
        skillRegistry.register(new CodeDebugSkill());

        reasoningGate = new DefaultReasoningGate();
        skillSelector = new SkillSelector(skillRegistry, reasoningGate);
        telemetryCollector = new SkillTelemetryCollector();

        skillRouter = new SkillRouter(skillRegistry, reasoningGate, skillSelector, telemetryCollector);
    }

    @Test
    void testReasoningGateDecisions() {
        SkillDescriptor searchDesc = skillRegistry.find("code.search").get().descriptor();
        assertEquals(ReasoningDecision.SKIP_MODEL, reasoningGate.evaluate("code.search", null, searchDesc));

        SkillDescriptor explainDesc = skillRegistry.find("code.explain").get().descriptor();
        assertEquals(ReasoningDecision.USE_MODEL, reasoningGate.evaluate("code.explain", null, explainDesc));

        SkillDescriptor inspectDesc = skillRegistry.find("code.inspect").get().descriptor();
        SkillContext clearCtx = new SkillContext("t1", "e1", Path.of("."), Map.of("symbol", "AuthService"));
        assertEquals(ReasoningDecision.SKIP_MODEL, reasoningGate.evaluate("code.inspect", clearCtx, inspectDesc));

        SkillContext ambigCtx = new SkillContext("t1", "e1", Path.of("."), Map.of("symbol", "AuthService", "ambiguous", true));
        assertEquals(ReasoningDecision.USE_MODEL, reasoningGate.evaluate("code.inspect", ambigCtx, inspectDesc));

        SkillDescriptor debugDesc = skillRegistry.find("code.debug").get().descriptor();
        SkillContext simpleError = new SkillContext("t1", "e1", Path.of("."), Map.of("errorLog", "NullPointerException at line 10"));
        assertEquals(ReasoningDecision.SKIP_MODEL, reasoningGate.evaluate("code.debug", simpleError, debugDesc));

        SkillContext complexError = new SkillContext("t1", "e1", Path.of("."), Map.of("errorLog", "Fatal error\nCaused by: java.io.IOException\nCaused by: java.net.SocketException"));
        assertEquals(ReasoningDecision.ESCALATE_MODEL, reasoningGate.evaluate("code.debug", complexError, debugDesc));
    }

    @Test
    void testSkillSelectionAndRouting() {
        Optional<SkillSelection> selection = skillSelector.select("find where authentication is configured", null);
        assertTrue(selection.isPresent());
        assertEquals("code.search", selection.get().skillId());
        assertEquals(ReasoningDecision.SKIP_MODEL, selection.get().reasoningDecision());

        selection = skillSelector.select("explain how auth filter works in detail", null);
        assertTrue(selection.isPresent());
        assertEquals("code.explain", selection.get().skillId());
        assertEquals(ReasoningDecision.USE_MODEL, selection.get().reasoningDecision());
    }

    @Test
    void testRouterDispatchAndTelemetry() {
        SkillContext ctx = new SkillContext("task-123", "exec-456", Path.of("."), Map.of("query", "Authentication"));
        SkillResult result = skillRouter.dispatch("code.search", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(1, telemetryCollector.getAllRecords().size());
        var record = telemetryCollector.getAllRecords().get(0);
        assertEquals("code.search", record.skillId());
        assertEquals("exec-456", record.executionId());
        assertTrue(record.durationMs() >= 0);
    }
}
