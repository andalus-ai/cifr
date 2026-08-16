package tech.kayys.aljabr.engine.skill.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.skill.*;
import tech.kayys.aljabr.engine.skill.impl.*;
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
    void testSkillSelectorDeterministicPatterns() {
        Optional<SkillSelection> selSearch = skillSelector.select("where is AuthService located?", null);
        assertTrue(selSearch.isPresent());
        assertEquals("code.search", selSearch.get().skillId());
        assertEquals("AuthService located?", selSearch.get().resolvedParameters().get("query"));
        assertEquals(ReasoningDecision.SKIP_MODEL, selSearch.get().reasoningDecision());

        Optional<SkillSelection> selInspect = skillSelector.select("definition of TokenValidator", null);
        assertTrue(selInspect.isPresent());
        assertEquals("code.inspect", selInspect.get().skillId());
        assertEquals("TokenValidator", selInspect.get().resolvedParameters().get("symbol"));

        Optional<SkillSelection> selTrace = skillSelector.select("references for authenticate", null);
        assertTrue(selTrace.isPresent());
        assertEquals("code.trace", selTrace.get().skillId());
        assertEquals("authenticate", selTrace.get().resolvedParameters().get("symbol"));

        Optional<SkillSelection> selTest = skillSelector.select("run test AuthServiceTest", null);
        assertTrue(selTest.isPresent());
        assertEquals("code.test", selTest.get().skillId());
        assertEquals("AuthServiceTest", selTest.get().resolvedParameters().get("testTarget"));

        Optional<SkillSelection> selExplain = skillSelector.select("explain how AuthService works", null);
        assertTrue(selExplain.isPresent());
        assertEquals("code.explain", selExplain.get().skillId());
        assertEquals(ReasoningDecision.USE_MODEL, selExplain.get().reasoningDecision());

        Optional<SkillSelection> selReview = skillSelector.select("review changes", null);
        assertTrue(selReview.isPresent());
        assertEquals("code.review", selReview.get().skillId());
        assertEquals(ReasoningDecision.USE_MODEL, selReview.get().reasoningDecision());

        Optional<SkillSelection> selExplicit = skillSelector.select("/search PaymentGateway", null);
        assertTrue(selExplicit.isPresent());
        assertEquals("code.search", selExplicit.get().skillId());
    }

    @Test
    void testTelemetryRecordingThroughRouter() {
        assertEquals(0, telemetryCollector.getTotalExecutionCount());

        SkillContext ctx = new SkillContext("task-1", "exec-101", Path.of("."), Map.of("testTarget", "PassedTest"));
        SkillResult result = skillRouter.dispatch("code.test", ctx).await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(1, telemetryCollector.getTotalExecutionCount());
        assertEquals(1.0, telemetryCollector.getSuccessRate());

        var record = telemetryCollector.getAllRecords().get(0);
        assertEquals("exec-101", record.executionId());
        assertEquals("code.test", record.skillId());
        assertEquals(ReasoningDecision.SKIP_MODEL, record.reasoningDecision());
        assertTrue(record.durationMs() >= 0);
    }
}
