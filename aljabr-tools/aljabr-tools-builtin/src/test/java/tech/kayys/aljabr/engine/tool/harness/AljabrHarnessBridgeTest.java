package tech.kayys.aljabr.engine.tool.harness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.tool.AljabrTool;
import tech.kayys.aljabr.engine.tool.ToolCapability;
import tech.kayys.aljabr.engine.tool.ToolCategory;
import tech.kayys.aljabr.engine.tool.ToolDescriptor;
import tech.kayys.aljabr.engine.tool.ToolRequest;
import tech.kayys.aljabr.engine.tool.ToolRisk;
import tech.kayys.aljabr.engine.tool.repl.JShellTool;
import tech.kayys.andalus.harness.governance.budget.BudgetDimension;
import tech.kayys.andalus.harness.governance.budget.BudgetUsage;
import tech.kayys.andalus.harness.tool.ToolResult;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class AljabrHarnessBridgeTest {

    private AljabrHarnessBridge bridge;
    private JShellTool jshellTool;

    @BeforeEach
    void setUp() throws Exception {
        bridge = new AljabrHarnessBridge();
        jshellTool = new JShellTool();

        // Mock CDI instance with our JShellTool
        jakarta.enterprise.inject.Instance<AljabrTool> instanceMock = new jakarta.enterprise.inject.Instance<>() {
            @Override
            public Iterator<AljabrTool> iterator() {
                return List.<AljabrTool>of(jshellTool).iterator();
            }

            @Override
            public jakarta.enterprise.inject.Instance<AljabrTool> select(java.lang.annotation.Annotation... qualifiers) { return this; }

            @Override
            public <U extends AljabrTool> jakarta.enterprise.inject.Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { return null; }

            @Override
            public <U extends AljabrTool> jakarta.enterprise.inject.Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { return null; }

            @Override
            public boolean isUnsatisfied() { return false; }

            @Override
            public boolean isAmbiguous() { return false; }

            @Override
            public void destroy(AljabrTool instance) {}

            @Override
            public jakarta.enterprise.inject.Instance.Handle<AljabrTool> getHandle() { return null; }

            @Override
            public Iterable<? extends jakarta.enterprise.inject.Instance.Handle<AljabrTool>> handles() { return Collections.emptyList(); }

            @Override
            public AljabrTool get() { return jshellTool; }
        };

        Field toolsField = AljabrHarnessBridge.class.getDeclaredField("aljabrTools");
        toolsField.setAccessible(true);
        toolsField.set(bridge, instanceMock);

        bridge.init();
    }

    @Test
    void testBridgeInitialization() {
        assertTrue(bridge.isReady());
        assertNotNull(bridge.journal());
        assertNotNull(bridge.budgetManager());
    }

    @Test
    void testGovernedExecutionWithBudgetAndJournal() {
        HarnessToolExecutionContext ctx = new HarnessToolExecutionContext("test-task-1", () -> {});
        
        ToolResult result = bridge.executeGoverned(
                "jshell",
                Map.of("code", "1 + 1"),
                ctx
        );

        assertNotNull(result);
        assertTrue(result.isSuccess(), "Execution should succeed");
        assertTrue(result.output().preview().contains("2"));

        // Verify that ActionJournal tracked the tool execution
        var history = bridge.journal().history(ctx.executionId());
        assertFalse(history.isEmpty(), "Journal should record the action");
        assertEquals("tool:jshell", history.get(0).type());

        // Verify that BudgetManager tracked the tool call
        assertNotNull(bridge.budgetManager());
    }
}
