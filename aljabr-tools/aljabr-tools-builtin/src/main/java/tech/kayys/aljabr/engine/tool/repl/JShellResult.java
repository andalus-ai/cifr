package tech.kayys.aljabr.engine.tool.repl;

import tech.kayys.aljabr.engine.tool.ToolResult;

/**
 * Result of a JShell interactive REPL evaluation.
 */
public record JShellResult(
        boolean success,
        String summary,
        String output,
        String stdout,
        String error
) implements ToolResult {

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public String getSummary() {
        return summary;
    }

    public static JShellResult ok(String output, String stdout) {
        String summary = output != null && !output.isBlank()
                ? (output.length() > 80 ? output.substring(0, 80) + "…" : output)
                : "Evaluation succeeded";
        return new JShellResult(true, summary, output, stdout, null);
    }

    public static JShellResult err(String error, String stdout) {
        String summary = error != null && !error.isBlank()
                ? (error.length() > 80 ? error.substring(0, 80) + "…" : error)
                : "Evaluation failed";
        return new JShellResult(false, summary, null, stdout, error);
    }
}
