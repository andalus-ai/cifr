package tech.kayys.aljabr.engine.tool.result;

import tech.kayys.aljabr.engine.tool.request.ToolRequest;
import java.time.Instant;
import java.util.List;

/**
 * A first-class artifact capturing a complete tool call including its normalized evidence.
 * Used for audit, telemetry, deduplication, and repair history.
 */
public record ToolObservation(
    ToolExecutionId callId,
    String toolId,
    ToolRequest request,
    ToolResult result,
    List<String> evidenceIds,
    CostMetrics cost,
    Instant timestamp
) {
    public ToolObservation {
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }

    public static ToolObservation of(ToolExecutionId callId, String toolId, ToolRequest request,
                                     ToolResult result, CostMetrics cost) {
        return new ToolObservation(callId, toolId, request, result, List.of(), cost, Instant.now());
    }
}
