package tech.kayys.aljabr.engine.orchestrator;

import tech.kayys.aljabr.engine.orchestrator.policy.ExecutionEnvironment;

import java.nio.file.Path;
import java.util.List;

public record ResolvedTool(
    ToolKind kind,
    List<String> command,
    Path workingDirectory,
    ExecutionEnvironment environment
) {
    public ResolvedTool {
        command = command != null ? List.copyOf(command) : List.of();
    }
}
