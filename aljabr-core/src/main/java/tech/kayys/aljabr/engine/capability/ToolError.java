package tech.kayys.aljabr.engine.capability;

/**
 * Structured tool execution error for closed-loop diagnostics.
 */
public record ToolError(
    String code,
    String message,
    boolean retryable,
    boolean userActionRequired
) {
    public static ToolError of(String code, String message, boolean retryable) {
        return new ToolError(code, message, retryable, false);
    }

    public static ToolError fatal(String code, String message) {
        return new ToolError(code, message, false, true);
    }
}
