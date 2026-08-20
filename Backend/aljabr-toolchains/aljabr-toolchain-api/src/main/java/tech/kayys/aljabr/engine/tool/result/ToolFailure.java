package tech.kayys.aljabr.engine.tool.result;
import tech.kayys.aljabr.engine.tool.protocol.ToolFailureCategory;
public record ToolFailure(ToolFailureCategory category, String message, boolean retryable) {
    public static ToolFailure of(ToolFailureCategory cat, String msg) {
        boolean retryable = cat == ToolFailureCategory.TIMEOUT || cat == ToolFailureCategory.NETWORK;
        return new ToolFailure(cat, msg, retryable);
    }
}
