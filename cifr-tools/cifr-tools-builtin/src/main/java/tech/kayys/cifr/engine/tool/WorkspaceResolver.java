package tech.kayys.cifr.engine.tool;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceResolver {

    private WorkspaceResolver() {}

    /** Set by CifrToolAdapter before executing each tool call, cleared after. */
    private static final ThreadLocal<String> SESSION_WORKSPACE = new ThreadLocal<>();

    /** Set the workspace root for the current thread (call before tool execution). */
    public static void setSessionWorkspace(String path) {
        if (path != null && !path.isBlank()) {
            SESSION_WORKSPACE.set(path);
        }
    }

    /** Clear the thread-local workspace override (call after tool execution). */
    public static void clearSessionWorkspace() {
        SESSION_WORKSPACE.remove();
    }

    public static Path resolveWorkspaceRoot() {
        // 1. Thread-local set by the current session (highest priority)
        String session = SESSION_WORKSPACE.get();
        if (session != null && !session.isBlank()) {
            Path p = Path.of(session).toAbsolutePath().normalize();
            if (Files.exists(p)) return p;
        }
        // 2. Environment variable
        String env = System.getenv("WORKSPACE_ROOT");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env).toAbsolutePath().normalize();
            if (Files.exists(p)) return p;
        }
        // 3. System property
        String prop = System.getProperty("workspace.root");
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop).toAbsolutePath().normalize();
            if (Files.exists(p)) return p;
        }
        // 4. Walk up from user.dir — generic fallback (no hardcoded project name)
        Path curr = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return curr;
    }
}
