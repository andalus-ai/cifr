package tech.kayys.cifr.engine.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves and validates paths against the workspace attached to one tool invocation. */
public final class WorkspaceResolver {

    private WorkspaceResolver() {}

    public static Path resolveWorkspaceRoot(ToolExecutionContext context) {
        if (context == null || context.workspaceRoot() == null || context.workspaceRoot().isEmpty()) {
            throw new IllegalStateException("Tool invocation has no explicit workspace root");
        }
        Path root = context.workspaceRoot().get().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Workspace root is not an existing directory: " + root);
        }
        try {
            return root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot resolve workspace root: " + root, e);
        }
    }

    /**
     * Resolves a model-supplied relative path without permitting traversal or symlink escape.
     * Existing targets are checked by their real path; for new targets, the nearest existing
     * ancestor is checked so a symlinked parent cannot escape the workspace.
     */
    public static Path resolveWorkspacePath(ToolExecutionContext context, String relativePath) {
        Path root = resolveWorkspaceRoot(context);
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Workspace-relative path is required");
        }
        Path supplied = Path.of(relativePath);
        if (supplied.isAbsolute()) {
            throw new IllegalArgumentException("Absolute paths are not allowed: " + relativePath);
        }
        Path candidate = root.resolve(supplied).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
        }
        try {
            Path existing = candidate;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            if (existing == null || !existing.toRealPath().startsWith(root)) {
                throw new IllegalArgumentException("Path escapes workspace through a symlink: " + relativePath);
            }
            if (Files.exists(candidate) && !candidate.toRealPath().startsWith(root)) {
                throw new IllegalArgumentException("Path escapes workspace through a symlink: " + relativePath);
            }
            return candidate;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot safely resolve workspace path: " + relativePath, e);
        }
    }
}
