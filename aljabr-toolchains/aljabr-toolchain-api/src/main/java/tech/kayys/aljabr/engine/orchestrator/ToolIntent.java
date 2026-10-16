package tech.kayys.aljabr.engine.orchestrator;

import java.util.Map;
import java.util.Optional;

public record ToolIntent(
    String id,
    String taskId,
    ToolKind kind,
    String purpose,
    Map<String, Object> arguments,
    Optional<String> reasonEvidenceId
) {
    public ToolIntent {
        arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
    }

    public static ToolIntent of(String id, String taskId, ToolKind kind, String purpose, Map<String, Object> arguments) {
        return new ToolIntent(id, taskId, kind, purpose, arguments, Optional.empty());
    }

    public static ToolIntent of(String id, String taskId, ToolKind kind, String purpose, Map<String, Object> arguments, String evidenceId) {
        return new ToolIntent(id, taskId, kind, purpose, arguments, Optional.ofNullable(evidenceId));
    }
}
