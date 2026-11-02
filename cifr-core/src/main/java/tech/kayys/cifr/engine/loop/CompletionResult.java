package tech.kayys.cifr.engine.loop;

/**
 * Objective evaluation outcome determining whether a goal is satisfied.
 */
public record CompletionResult(
    boolean isCompleted,
    String summary
) {
    public static CompletionResult completed(String summary) {
        return new CompletionResult(true, summary);
    }

    public static CompletionResult notCompleted(String summary) {
        return new CompletionResult(false, summary);
    }
}
