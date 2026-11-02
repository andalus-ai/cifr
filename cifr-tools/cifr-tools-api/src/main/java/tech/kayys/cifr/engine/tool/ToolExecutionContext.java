package tech.kayys.cifr.engine.tool;

import java.nio.file.Path;
import java.util.Optional;
import tech.kayys.cifr.engine.loop.CodingPolicy;

/**
 * Execution context threaded through every tool invocation.
 * <p>
 * {@code workspaceRoot} is the resolved filesystem path for the active workspace.
 * Workspace identity is carried with each invocation so asynchronous tool execution
 * never depends on thread-local state.
 * </p>
 */
public record ToolExecutionContext(
    String taskId,
    String workspaceId,
    CodingPolicy policy,
    Optional<String> checkpoint,
    Runnable cancellation,
    Optional<Path> workspaceRoot
) {
    public ToolExecutionContext(String taskId, String workspaceId, CodingPolicy policy,
            Optional<String> checkpoint, Runnable cancellation) {
        this(taskId, workspaceId, policy, checkpoint, cancellation, Optional.empty());
    }

    public ToolExecutionContext {
        checkpoint = checkpoint != null ? checkpoint : Optional.empty();
        cancellation = cancellation != null ? cancellation : () -> {};
        workspaceRoot = workspaceRoot != null ? workspaceRoot : Optional.empty();
    }

    /** Convenience factory without workspaceRoot. */
    public static ToolExecutionContext of(
            String taskId,
            String workspaceId,
            CodingPolicy policy,
            Optional<String> checkpoint,
            Runnable cancellation) {
        return new ToolExecutionContext(
                taskId, workspaceId, policy, checkpoint, cancellation, Optional.empty());
    }

    /** Convenience factory with explicit workspaceRoot. */
    public static ToolExecutionContext of(
            String taskId,
            String workspaceId,
            CodingPolicy policy,
            Optional<String> checkpoint,
            Runnable cancellation,
            Path workspaceRoot) {
        return new ToolExecutionContext(
                taskId, workspaceId, policy, checkpoint, cancellation,
                Optional.ofNullable(workspaceRoot));
    }
}

