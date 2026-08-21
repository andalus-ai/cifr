package tech.kayys.aljabr.engine.capability;

import java.time.Instant;
import java.util.Map;

/**
 * Execution context carrying correlation ID, cancellation tokens, deadline, and workspace root.
 */
public record ToolExecutionContext(
    String executionId,
    String correlationId,
    Instant deadline,
    CancellationToken cancellationToken,
    String workspacePath,
    Map<String, Object> attributes
) {
    public ToolExecutionContext {
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    public static ToolExecutionContext of(String executionId, String workspacePath) {
        return new ToolExecutionContext(
            executionId,
            "corr-" + java.util.UUID.randomUUID(),
            Instant.now().plusSeconds(60),
            CancellationToken.create(),
            workspacePath,
            Map.of()
        );
    }
}
