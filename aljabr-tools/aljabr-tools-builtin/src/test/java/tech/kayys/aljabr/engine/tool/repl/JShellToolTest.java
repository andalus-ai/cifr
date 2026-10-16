package tech.kayys.aljabr.engine.tool.repl;

import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.tool.ToolRequest;
import tech.kayys.aljabr.engine.tool.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JShellToolTest {

    @Test
    void testBasicArithmetic() {
        try (JShellImpl shell = new JShellImpl()) {
            JShellImpl.Result res = shell.evaluate("1 + 1");
            assertTrue(res.success());
            assertEquals("2", res.output());
        }
    }

    @Test
    void testPrintStreamCapture() {
        try (JShellImpl shell = new JShellImpl()) {
            JShellImpl.Result res = shell.evaluate("System.out.println(\"Aljabr JShell!\");");
            assertTrue(res.success());
            assertTrue(res.output().contains("Aljabr JShell!"));
        }
    }

    @Test
    void testCompilationError() {
        try (JShellImpl shell = new JShellImpl()) {
            JShellImpl.Result res = shell.evaluate("invalidSyntax()();");
            assertFalse(res.success());
            assertNotNull(res.error());
        }
    }

    @Test
    void testAljabrToolExecution() {
        JShellTool tool = new JShellTool();
        ToolRequest request = new ToolRequest(
                "jshell",
                Map.of("code", "int a = 10; int b = 32; a + b"),
                Optional.empty(),
                List.of()
        );

        ToolResult result = tool.execute(request, null).await().indefinitely();
        assertTrue(result.isSuccess());
        assertTrue(result instanceof JShellResult);
        JShellResult jr = (JShellResult) result;
        assertTrue(jr.output().contains("42"));
    }

    @Test
    void testAljabrToolSessionState() {
        JShellTool tool = new JShellTool();
        ToolRequest req1 = new ToolRequest(
                "jshell",
                Map.of("code", "String name = \"Aljabr\";", "session", true),
                Optional.empty(),
                List.of()
        );
        ToolResult res1 = tool.execute(req1, null).await().indefinitely();
        assertTrue(res1.isSuccess());

        ToolRequest req2 = new ToolRequest(
                "jshell",
                Map.of("code", "name.toUpperCase()", "session", true),
                Optional.empty(),
                List.of()
        );
        ToolResult res2 = tool.execute(req2, null).await().indefinitely();
        assertTrue(res2.isSuccess());
        JShellResult jr = (JShellResult) res2;
        assertEquals("\"ALJABR\"", jr.output());
    }

    @Test
    void testEmptyCodeValidation() {
        JShellTool tool = new JShellTool();
        ToolRequest req = new ToolRequest(
                "jshell",
                Map.of("code", ""),
                Optional.empty(),
                List.of()
        );
        ToolResult res = tool.execute(req, null).await().indefinitely();
        assertFalse(res.isSuccess());
    }
}
