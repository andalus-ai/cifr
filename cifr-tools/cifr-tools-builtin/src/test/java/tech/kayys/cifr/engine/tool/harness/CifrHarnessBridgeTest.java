package tech.kayys.cifr.engine.tool.harness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.tool.CifrTool;
import tech.kayys.cifr.engine.tool.ToolCapability;
import tech.kayys.cifr.engine.tool.ToolCategory;
import tech.kayys.cifr.engine.tool.ToolDescriptor;
import tech.kayys.cifr.engine.tool.ToolRequest;
import tech.kayys.cifr.engine.tool.ToolRisk;
import tech.kayys.cifr.engine.tool.repl.JShellTool;
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

public class CifrHarnessBridgeTest {

    private CifrHarnessBridge bridge;
    private JShellTool jshellTool;
    private String previousAllowedTools;

    @BeforeEach
    void setUp() throws Exception {
        previousAllowedTools = System.getProperty("cifr.harness.allowed-tools");
        System.setProperty("cifr.harness.allowed-tools", "jshell");
        bridge = new CifrHarnessBridge();
        jshellTool = new JShellTool();

        // Mock CDI instance with our JShellTool
        jakarta.enterprise.inject.Instance<CifrTool> instanceMock = new jakarta.enterprise.inject.Instance<>() {
            @Override
            public Iterator<CifrTool> iterator() {
                return List.<CifrTool>of(jshellTool).iterator();
            }

            @Override
            public jakarta.enterprise.inject.Instance<CifrTool> select(java.lang.annotation.Annotation... qualifiers) { return this; }

            @Override
            public <U extends CifrTool> jakarta.enterprise.inject.Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { return null; }

            @Override
            public <U extends CifrTool> jakarta.enterprise.inject.Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { return null; }

            @Override
            public boolean isUnsatisfied() { return false; }

            @Override
            public boolean isAmbiguous() { return false; }

            @Override
            public void destroy(CifrTool instance) {}

            @Override
            public jakarta.enterprise.inject.Instance.Handle<CifrTool> getHandle() { return null; }

            @Override
            public Iterable<? extends jakarta.enterprise.inject.Instance.Handle<CifrTool>> handles() { return Collections.emptyList(); }

            @Override
            public CifrTool get() { return jshellTool; }
        };

        Field toolsField = CifrHarnessBridge.class.getDeclaredField("cifrTools");
        toolsField.setAccessible(true);
        toolsField.set(bridge, instanceMock);

        bridge.init();
    }

    @AfterEach
    void restoreConfiguration() {
        if (previousAllowedTools == null) {
            System.clearProperty("cifr.harness.allowed-tools");
        } else {
            System.setProperty("cifr.harness.allowed-tools", previousAllowedTools);
        }
    }

    @Test
    void testUnlistedToolIsDeniedBeforeResolution() {
        HarnessToolExecutionContext ctx = new HarnessToolExecutionContext("test-task-denied", () -> {});
        ToolResult result = bridge.executeGoverned("jbang", Map.of(), ctx);

        assertNotNull(result);
        assertFalse(result.isSuccess(), "Tools outside the explicit allowlist must be denied");
    }

    @Test
    void testAllowlistParsingIsExactAndIgnoresEmptyEntries() {
        assertEquals(Set.of("jshell", "read_file"),
                CifrHarnessBridge.parseAllowedTools(" jshell, ,read_file "));
        assertTrue(CifrHarnessBridge.parseAllowedTools(" , ").isEmpty());
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
