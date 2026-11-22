package tech.kayys.cifr.engine.tool.script;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-risk Java script execution tool. It remains disabled until Cifr has
 * per-invocation approval and an OS-isolated execution backend.
 */
@ApplicationScoped
public class JBangTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "jbang",
                "Execute Java scripts and snippets. Requires an approved isolated execution environment.",
                ToolCategory.SHELL,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "code", Map.of("type", "string", "description", "Java code snippet or script to run instantly."),
                                "script_path", Map.of("type", "string", "description", "Path to an existing .java/.jsh file or remote URL to run."),
                                "dependencies", Map.of("type", "array", "description", "Maven coordinates for dependencies.",
                                        "items", Map.of("type", "string")),
                                "args", Map.of("type", "array", "description", "Arguments passed to the Java application.",
                                        "items", Map.of("type", "string")),
                                "jbang_args", Map.of("type", "array", "description", "Additional CLI flags passed to jbang.",
                                        "items", Map.of("type", "string")),
                                "action", Map.of("type", "string", "description", "Action to perform: run, install, or info.",
                                        "enum", List.of("run", "install", "info")),
                                "timeout", Map.of("type", "integer", "description", "Execution timeout in seconds.")
                        )
                ),
                Set.of(ToolCapability.EXECUTE_PROCESS, ToolCapability.NETWORK_ACCESS),
                ToolRisk.CRITICAL
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> JBangResult.err(
                "JBang is disabled: arbitrary scripts require per-invocation approval and an OS-isolated execution backend",
                "", "", -1));
    }
}
