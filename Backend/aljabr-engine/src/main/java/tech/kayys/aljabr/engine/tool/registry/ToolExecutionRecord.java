package tech.kayys.aljabr.engine.tool.registry;
import tech.kayys.aljabr.engine.tool.result.ToolStatus;
import java.time.Instant;
public record ToolExecutionRecord(
    String id,
    String taskId,
    String toolName,
    String inputHash,
    String outputHash,
    long workspaceRevision,
    Instant executedAt,
    ToolStatus status
) {
    public static ToolExecutionRecord of(String taskId, String toolName, String inputHash, ToolStatus status) {
        return new ToolExecutionRecord("ter-" + java.util.UUID.randomUUID(), taskId, toolName, inputHash, null, 0L, Instant.now(), status);
    }
}
