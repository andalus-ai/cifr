package tech.kayys.cifr.engine.tool.harness;

import tech.kayys.andalus.harness.context.DefaultHarnessIdentity;
import tech.kayys.andalus.harness.context.HarnessIdentity;
import tech.kayys.andalus.harness.execution.state.ExecutionId;
import tech.kayys.andalus.harness.resource.DefaultResourceScope;
import tech.kayys.andalus.harness.resource.ResourceScope;
import tech.kayys.andalus.harness.tool.ArtifactStore;
import tech.kayys.andalus.harness.tool.CancellationToken;
import tech.kayys.andalus.harness.tool.ToolExecutionContext;
import tech.kayys.andalus.harness.tool.ToolLogger;

import java.nio.file.Path;
import java.util.Optional;

/** Carries CIFR invocation workspace identity through the Andalus harness boundary. */
public class HarnessToolExecutionContext implements ToolExecutionContext {

    private final ExecutionId executionId;
    private final HarnessIdentity identity;
    private final ResourceScope resources;
    private final CancellationToken cancellation;
    private final ToolLogger logger;
    private final ArtifactStore artifacts;
    private final String workspaceId;
    private final Optional<Path> workspaceRoot;

    public HarnessToolExecutionContext(String taskId, Runnable cifrCancellation) {
        this(taskId, null, null, cifrCancellation);
    }

    public HarnessToolExecutionContext(String taskId, String workspaceId, Path workspaceRoot,
            Runnable cifrCancellation) {
        this.executionId = ExecutionId.of(taskId);
        this.identity = DefaultHarnessIdentity.of(taskId);
        this.resources = DefaultResourceScope.allowAll();
        this.cancellation = bridgeCancellation(cifrCancellation);
        this.logger = ToolLogger.noop();
        this.artifacts = ArtifactStore.inMemory();
        this.workspaceId = workspaceId;
        this.workspaceRoot = Optional.ofNullable(workspaceRoot).map(p -> p.toAbsolutePath().normalize());
    }

    public static HarnessToolExecutionContext from(
            tech.kayys.cifr.engine.tool.ToolExecutionContext cifrCtx) {
        String taskId = cifrCtx.taskId() != null ? cifrCtx.taskId() : "cifr";
        String workspaceId = cifrCtx.workspaceId();
        Path workspaceRoot = cifrCtx.workspaceRoot() != null
                ? cifrCtx.workspaceRoot().orElse(null) : null;
        return new HarnessToolExecutionContext(taskId, workspaceId, workspaceRoot, cifrCtx.cancellation());
    }

    public String workspaceId() {
        return workspaceId;
    }

    public Optional<Path> workspaceRoot() {
        return workspaceRoot;
    }

    @Override public ExecutionId executionId() { return executionId; }
    @Override public HarnessIdentity identity() { return identity; }
    @Override public ResourceScope resources() { return resources; }
    @Override public CancellationToken cancellation() { return cancellation; }
    @Override public ToolLogger logger() { return logger; }
    @Override public ArtifactStore artifacts() { return artifacts; }

    private static CancellationToken bridgeCancellation(Runnable cifrCancellation) {
        return new CancellationToken() {
            private volatile boolean cancelled = false;
            @Override public boolean isCancellationRequested() { return cancelled; }
            @Override public void cancel() {
                cancelled = true;
                if (cifrCancellation != null) cifrCancellation.run();
            }
        };
    }
}
