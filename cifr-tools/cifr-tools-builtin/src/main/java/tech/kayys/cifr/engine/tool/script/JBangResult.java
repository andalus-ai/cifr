package tech.kayys.cifr.engine.tool.script;

import tech.kayys.cifr.engine.tool.ToolResult;

/**
 * Result of a JBang script or instant snippet execution.
 */
public record JBangResult(
        boolean success,
        String summary,
        String output,
        String stdout,
        String stderr,
        int exitCode
) implements ToolResult {

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public String getSummary() {
        return summary;
    }

    public static JBangResult ok(String output, String stdout, String stderr, int exitCode) {
        String summary = output != null && !output.isBlank()
                ? (output.length() > 80 ? output.substring(0, 80) + "…" : output)
                : "JBang execution succeeded (exit code " + exitCode + ")";
        return new JBangResult(true, summary, output, stdout, stderr, exitCode);
    }

    public static JBangResult err(String error, String stdout, String stderr, int exitCode) {
        String summary = error != null && !error.isBlank()
                ? (error.length() > 80 ? error.substring(0, 80) + "…" : error)
                : "JBang execution failed (exit code " + exitCode + ")";
        return new JBangResult(false, summary, error, stdout, stderr, exitCode);
    }
}
