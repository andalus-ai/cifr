package tech.kayys.cifr.engine.tool;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the workspace from an invocation context, then explicit process configuration. */
public final class WorkspaceResolver {

    private WorkspaceResolver() {}

    public static Path resolveWorkspaceRoot(ToolExecutionContext context) {
        if (context != null && context.workspaceRoot() != null && context.workspaceRoot().isPresent()) {
            Path root = context.workspaceRoot().get().toAbsolutePath().normalize();
            if (Files.isDirectory(root)) return root;
            throw new IllegalArgumentException("Workspace root is not an existing directory: " + root);
        }
        return resolveConfiguredWorkspaceRoot();
    }

    /** Compatibility fallback for tools invoked outside a workspace-aware request. */
    public static Path resolveWorkspaceRoot() {
        return resolveConfiguredWorkspaceRoot();
    }

    private static Path resolveConfiguredWorkspaceRoot() {
        String env = System.getenv("WORKSPACE_ROOT");
        if (env != null && !env.isBlank()) {
            Path root = Path.of(env).toAbsolutePath().normalize();
            if (Files.isDirectory(root)) return root;
        }
        String property = System.getProperty("workspace.root");
        if (property != null && !property.isBlank()) {
            Path root = Path.of(property).toAbsolutePath().normalize();
            if (Files.isDirectory(root)) return root;
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
}
