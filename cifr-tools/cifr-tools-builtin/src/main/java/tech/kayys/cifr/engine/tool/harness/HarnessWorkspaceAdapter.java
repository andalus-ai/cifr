package tech.kayys.cifr.engine.tool.harness;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.tool.WorkspaceResolver;
import tech.kayys.andalus.harness.workspace.LocalWorkspace;
import tech.kayys.andalus.harness.workspace.LocalWorkspaceManager;
import tech.kayys.andalus.harness.workspace.Workspace;
import tech.kayys.andalus.harness.workspace.WorkspaceId;
import tech.kayys.andalus.harness.workspace.WorkspacePolicy;
import tech.kayys.andalus.harness.workspace.WorkspaceType;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bridges Cifr's {@link WorkspaceResolver} with the Andalus Harness
 * {@link LocalWorkspaceManager}, enabling tools to work in properly governed,
 * snapshot-capable workspaces instead of raw filesystem paths.
 *
 * <p>When a tool requests a workspace by ID, this adapter:
 * <ol>
 *   <li>Resolves the root path via {@link WorkspaceResolver}.</li>
 *   <li>Creates or retrieves a {@link LocalWorkspace} backed by that path.</li>
 *   <li>Returns the workspace for structured file I/O (path containment enforcement,
 *       snapshot/diff support via the harness).</li>
 * </ol>
 *
 * <p>Falls back gracefully when no workspace root is configured — returns
 * {@code Optional.empty()}, allowing tools to use the raw path instead.</p>
 */
@ApplicationScoped
public class HarnessWorkspaceAdapter {

    private static final Logger LOG = Logger.getLogger(HarnessWorkspaceAdapter.class);

    /** In-memory registry of active harness workspaces, keyed by workspace ID string. */
    private final ConcurrentMap<String, LocalWorkspace> activeWorkspaces = new ConcurrentHashMap<>();

    /**
     * Resolves a {@link LocalWorkspace} for the given workspace ID.
     *
     * <p>If a workspace with this ID is already open, it is returned directly.
     * Otherwise, a new {@link LocalWorkspace} is created using the resolved root path.</p>
     *
     * @param workspaceId the Cifr workspace ID (from {@link tech.kayys.cifr.engine.tool.ToolExecutionContext})
     * @return an active {@link LocalWorkspace}, or {@code Optional.empty()} if no root is available
     */
    public Optional<LocalWorkspace> resolve(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return Optional.empty();
        }

        // Fast path: already open
        LocalWorkspace existing = activeWorkspaces.get(workspaceId);
        if (existing != null) {
            return Optional.of(existing);
        }

        // Resolve filesystem path via Cifr WorkspaceResolver
        Path root = WorkspaceResolver.resolveWorkspaceRoot();
        if (root == null) {
            LOG.warnf("No workspace root resolved for workspaceId=%s — harness workspace unavailable", workspaceId);
            return Optional.empty();
        }

        try {
            LocalWorkspace workspace = new LocalWorkspace(
                    WorkspaceId.of(workspaceId),
                    WorkspaceType.LOCAL,
                    WorkspacePolicy.readWrite(),
                    root,
                    false  // not ephemeral — the workspace root is managed externally
            );
            activeWorkspaces.put(workspaceId, workspace);
            LOG.infof("Opened harness workspace: id=%s, root=%s", workspaceId, root);
            return Optional.of(workspace);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create harness workspace for id=%s at root=%s", workspaceId, root);
            return Optional.empty();
        }
    }

    /**
     * Returns the underlying root {@link Path} for a workspace without opening a full workspace.
     * Useful for tools that only need the path, not the full harness workspace.
     */
    public Optional<Path> resolveRoot(String workspaceId) {
        // Shortcut: resolve directly from WorkspaceResolver if workspace ID matches current context
        Path root = WorkspaceResolver.resolveWorkspaceRoot();
        if (root != null) {
            return Optional.of(root);
        }
        // Fall back: try to open the workspace and read its root marker
        return resolve(workspaceId).map(w -> {
            String relRoot = w.root().value();
            return relRoot.isEmpty() ? WorkspaceResolver.resolveWorkspaceRoot()
                    : Path.of(relRoot).toAbsolutePath();
        });
    }

    /**
     * Releases an active workspace from this adapter's registry.
     * Does NOT delete the workspace's files (the workspace is not ephemeral).
     */
    public void release(String workspaceId) {
        activeWorkspaces.remove(workspaceId);
        LOG.debugf("Released harness workspace from adapter: %s", workspaceId);
    }
}
