package tech.kayys.cifr.engine.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import tech.kayys.cifr.engine.loop.CodingPolicy;

/** Immutable, invocation-scoped identity and filesystem boundary for a CIFR tool call. */
public record ToolExecutionContext(
    String taskId,
    String workspaceId,
    CodingPolicy policy,
    Optional<String> checkpoint,
    Runnable cancellation,
    Optional<Path> workspaceRoot
) {
    public ToolExecutionContext {
        checkpoint = checkpoint == null ? Optional.empty() : checkpoint;
        workspaceRoot = workspaceRoot == null ? Optional.empty()
                : workspaceRoot.map(p -> p.toAbsolutePath().normalize());
        cancellation = cancellation == null ? () -> {} : cancellation;
    }

    /** Compatibility factory. Workspace-scoped tools fail closed if no root is supplied. */
    public static ToolExecutionContext of(String taskId, String workspaceId, CodingPolicy policy,
            Optional<String> checkpoint, Runnable cancellation) {
        return new ToolExecutionContext(taskId, workspaceId, policy, checkpoint, cancellation, Optional.empty());
    }

    public static ToolExecutionContext of(String taskId, String workspaceId, CodingPolicy policy,
            Optional<String> checkpoint, Runnable cancellation, Path workspaceRoot) {
        return new ToolExecutionContext(taskId, workspaceId, policy, checkpoint, cancellation,
                Optional.ofNullable(workspaceRoot));
    }

    public Path requireWorkspaceRoot() {
        return workspaceRoot.orElseThrow(() ->
                new IllegalStateException("Workspace root is required for this tool invocation"));
    }

    /**
     * Resolve a user-supplied relative path inside this invocation's workspace.
     * Existing symlinks (including symlinked parents for new files) are checked
     * against the real workspace root to prevent escaping through symlinks.
     */
    public Path resolveWorkspacePath(String suppliedPath) {
        if (suppliedPath == null || suppliedPath.isBlank()) {
            throw new IllegalArgumentException("Workspace path cannot be empty");
        }
        try {
        Path root = requireWorkspaceRoot().toRealPath();
        Path supplied = Path.of(suppliedPath);
        Path candidate = root.resolve(supplied).normalize();
        if (!candidate.startsWith(root)) {
            throw new SecurityException("Path escapes workspace: " + suppliedPath);
        }

        Path existing = candidate;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null || !existing.toRealPath().startsWith(root)) {
            throw new SecurityException("Path resolves outside workspace: " + suppliedPath);
        }
        if (Files.exists(candidate) && !candidate.toRealPath().startsWith(root)) {
            throw new SecurityException("Path resolves outside workspace: " + suppliedPath);
        }
        return candidate;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to resolve path within workspace", e);
        }
    }
}
