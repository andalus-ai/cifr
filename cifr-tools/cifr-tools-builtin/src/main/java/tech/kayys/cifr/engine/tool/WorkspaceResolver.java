package tech.kayys.cifr.engine.tool;

import java.nio.file.Path;

/**
 * Compatibility facade for workspace resolution. Resolution is deliberately
 * invocation-scoped; ambient ThreadLocal, environment and user.dir fallbacks
 * are unsafe in concurrent asynchronous agent executions.
 */
public final class WorkspaceResolver {
    private WorkspaceResolver() {}

    public static Path resolveWorkspaceRoot(ToolExecutionContext context) {
        if (context == null) {
            throw new IllegalStateException("Tool execution context is required");
        }
        return context.requireWorkspaceRoot();
    }
}
