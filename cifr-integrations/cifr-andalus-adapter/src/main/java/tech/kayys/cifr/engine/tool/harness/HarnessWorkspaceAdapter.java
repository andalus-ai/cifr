package tech.kayys.cifr.engine.tool.harness;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.tool.ToolExecutionContext;
import tech.kayys.cifr.engine.tool.WorkspaceResolver;
import tech.kayys.andalus.harness.workspace.LocalWorkspace;
import tech.kayys.andalus.harness.workspace.WorkspaceId;
import tech.kayys.andalus.harness.workspace.WorkspacePolicy;
import tech.kayys.andalus.harness.workspace.WorkspaceType;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Bridges invocation-scoped CIFR workspaces to Andalus harness workspaces. */
@ApplicationScoped
public class HarnessWorkspaceAdapter {

    private static final Logger LOG = Logger.getLogger(HarnessWorkspaceAdapter.class);
    private final ConcurrentMap<String, ActiveWorkspace> activeWorkspaces = new ConcurrentHashMap<>();

    private record ActiveWorkspace(LocalWorkspace workspace, Path root) {}

    /**
     * Resolves a harness workspace using the root supplied by this exact invocation.
     * A workspace ID cannot be rebound to a different filesystem root while cached.
     */
    public Optional<LocalWorkspace> resolve(String workspaceId, ToolExecutionContext context) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        Path root = WorkspaceResolver.resolveWorkspaceRoot(context);
        ActiveWorkspace cached = activeWorkspaces.get(workspaceId);
        if (cached != null) {
            if (!cached.root().equals(root)) {
                throw new IllegalStateException("Workspace ID is already bound to a different root: " + workspaceId);
            }
            return Optional.of(cached.workspace());
        }

        try {
            LocalWorkspace workspace = new LocalWorkspace(
                    WorkspaceId.of(workspaceId),
                    WorkspaceType.LOCAL,
                    WorkspacePolicy.readWrite(),
                    root,
                    false
            );
            ActiveWorkspace candidate = new ActiveWorkspace(workspace, root);
            ActiveWorkspace raced = activeWorkspaces.putIfAbsent(workspaceId, candidate);
            if (raced != null) {
                if (!raced.root().equals(root)) {
                    throw new IllegalStateException("Workspace ID is already bound to a different root: " + workspaceId);
                }
                return Optional.of(raced.workspace());
            }
            LOG.infof("Opened harness workspace: id=%s, root=%s", workspaceId, root);
            return Optional.of(workspace);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create harness workspace for id=%s at root=%s", workspaceId, root);
            return Optional.empty();
        }
    }

    /** Returns the invocation's validated workspace root without consulting process globals. */
    public Optional<Path> resolveRoot(String workspaceId, ToolExecutionContext context) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        Path root = WorkspaceResolver.resolveWorkspaceRoot(context);
        ActiveWorkspace cached = activeWorkspaces.get(workspaceId);
        if (cached != null && !cached.root().equals(root)) {
            throw new IllegalStateException("Workspace ID is already bound to a different root: " + workspaceId);
        }
        return Optional.of(root);
    }

    /**
     * Legacy entry points intentionally fail closed. Callers must pass the invocation context
     * so asynchronous and concurrent requests cannot borrow another request's workspace.
     */
    @Deprecated
    public Optional<LocalWorkspace> resolve(String workspaceId) {
        throw new IllegalStateException("ToolExecutionContext is required to resolve a harness workspace");
    }

    @Deprecated
    public Optional<Path> resolveRoot(String workspaceId) {
        throw new IllegalStateException("ToolExecutionContext is required to resolve a workspace root");
    }

    public void release(String workspaceId) {
        activeWorkspaces.remove(workspaceId);
        LOG.debugf("Released harness workspace from adapter: %s", workspaceId);
    }
}
