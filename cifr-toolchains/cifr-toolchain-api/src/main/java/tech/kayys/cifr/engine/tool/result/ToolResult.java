package tech.kayys.cifr.engine.tool.result;
public record ToolResult(
    ToolExecutionId executionId,
    ToolStatus status,
    ToolPayload payload,
    ToolEvidence evidence,
    ToolMetrics metrics
) {
    public boolean isSuccess() { return status == ToolStatus.SUCCESS || status == ToolStatus.CACHED; }
    public static ToolResult success(ToolPayload payload) {
        return new ToolResult(ToolExecutionId.generate(), ToolStatus.SUCCESS, payload, null, null);
    }
    public static ToolResult failure(String message) {
        return new ToolResult(ToolExecutionId.generate(), ToolStatus.FAILED, ToolPayload.text(message), null, null);
    }
}
