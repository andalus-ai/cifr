package tech.kayys.cifr.engine.tool.script;

import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.tool.ToolRequest;
import tech.kayys.cifr.engine.tool.ToolResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JBangToolTest {

    @Test
    void testExecutableDiscovery() {
        Optional<Path> bin = JBangBootstrap.findExecutable();
        assertNotNull(bin);
    }

    @Test
    void testSourcePreparationWrapping() {
        String code = "System.out.println(\"cifr\");";
        String prepared = JBangImpl.prepareSource(code, List.of("org.apache.commons:commons-lang3:3.14.0"));

        assertTrue(prepared.contains("//DEPS org.apache.commons:commons-lang3:3.14.0"));
        assertTrue(prepared.contains("class InstantScript"));
        assertTrue(prepared.contains("public static void main"));
    }

    @Test
    void testToolDescriptor() {
        JBangTool tool = new JBangTool();
        var descriptor = tool.descriptor();

        assertNotNull(descriptor);
        assertEquals("jbang", descriptor.name());
        assertTrue(descriptor.inputSchema().containsKey("properties"));
    }

    @Test
    void testActionInfo() {
        JBangTool tool = new JBangTool();
        ToolRequest req = new ToolRequest(
                "jbang",
                Map.of("action", "info"),
                Optional.empty(),
                List.of()
        );
        ToolResult result = tool.execute(req, null).await().indefinitely();
        assertNotNull(result);
        if (result.isSuccess()) {
            assertTrue(result instanceof JBangResult);
        }
    }

    @Test
    void testInstantSnippetExecution() {
        Optional<Path> bin = JBangBootstrap.findExecutable();
        if (bin.isEmpty()) {
            return;
        }

        JBangTool tool = new JBangTool();
        ToolRequest req = new ToolRequest(
                "jbang",
                Map.of("code", "System.out.println(\"CIFR_INSTANT:\" + (20 + 22));"),
                Optional.empty(),
                List.of()
        );
        ToolResult result = tool.execute(req, null).await().indefinitely();

        assertTrue(result.isSuccess(), "Execution failed: " + result.getSummary());
        assertTrue(result instanceof JBangResult);
        JBangResult jr = (JBangResult) result;
        assertTrue(jr.output().contains("CIFR_INSTANT:42"));
    }

    @Test
    void testMissingArgumentsValidation() {
        JBangTool tool = new JBangTool();
        ToolRequest req = new ToolRequest(
                "jbang",
                Map.of("action", "run"),
                Optional.empty(),
                List.of()
        );
        ToolResult result = tool.execute(req, null).await().indefinitely();
        assertFalse(result.isSuccess());
    }
}
