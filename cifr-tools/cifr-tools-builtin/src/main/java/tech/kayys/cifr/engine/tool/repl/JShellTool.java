package tech.kayys.cifr.engine.tool.repl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interactive Java REPL descriptor. Direct execution is disabled because JShell
 * evaluates arbitrary JVM code inside the Cifr host process.
 */
@ApplicationScoped
public class JShellTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "jshell",
                "Evaluate arbitrary Java code interactively using JShell. Requires an approved isolated execution environment.",
                ToolCategory.SHELL,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "code", Map.of("type", "string", "description", "Java snippet(s) to evaluate"),
                                "session", Map.of("type", "boolean", "description", "Persist state across calls (default false)"),
                                "timeout", Map.of("type", "integer", "description", "Max eval time ms (default 10000)")
                        ),
                        "required", List.of("code")
                ),
                Set.of(ToolCapability.EXECUTE_PROCESS, ToolCapability.NETWORK_ACCESS),
                ToolRisk.CRITICAL
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> JShellResult.err(
                "JShell is disabled: arbitrary JVM code requires per-invocation approval and an OS-isolated execution backend",
                null));
    }
}
