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

/**
 * Bridges Cifr's {@link tech.kayys.cifr.engine.tool.ToolExecutionContext} record
 * to the Andalus Harness {@link ToolExecutionContext} interface required by
 * {@code GovernedToolExecutor}.
 *
 * <p>The harness context adds identity, resource scope, logging, artifact store
 * and cancellation on top of Cifr's minimal task/workspace IDs.</p>
 */
public class HarnessToolExecutionContext implements ToolExecutionContext {

    private final ExecutionId executionId;
    private final HarnessIdentity identity;
    private final ResourceScope resources;
    private final CancellationToken cancellation;
    private final ToolLogger logger;
    private final ArtifactStore artifacts;

    /**
     * Constructs a fully specified context.
     */
    public HarnessToolExecutionContext(
            String taskId,
            Runnable cifrCancellation) {
        this.executionId = ExecutionId.of(taskId);
        this.identity = DefaultHarnessIdentity.of(taskId);
        this.resources = DefaultResourceScope.allowAll();
        this.cancellation = bridgeCancellation(cifrCancellation);
        this.logger = ToolLogger.noop();
        this.artifacts = ArtifactStore.inMemory();
    }

    /**
     * Adapts an Cifr {@link tech.kayys.cifr.engine.tool.ToolExecutionContext}
     * into a harness context.
     */
    public static HarnessToolExecutionContext from(
            tech.kayys.cifr.engine.tool.ToolExecutionContext cifrCtx) {
        String taskId = cifrCtx.taskId() != null ? cifrCtx.taskId() : "cifr";
        Runnable cancellation = cifrCtx.cancellation();
        return new HarnessToolExecutionContext(taskId, cancellation);
    }

    @Override
    public ExecutionId executionId() {
        return executionId;
    }

    @Override
    public HarnessIdentity identity() {
        return identity;
    }

    @Override
    public ResourceScope resources() {
        return resources;
    }

    @Override
    public CancellationToken cancellation() {
        return cancellation;
    }

    @Override
    public ToolLogger logger() {
        return logger;
    }

    @Override
    public ArtifactStore artifacts() {
        return artifacts;
    }

    // ---- Private helpers ----

    private static CancellationToken bridgeCancellation(Runnable cifrCancellation) {
        return new CancellationToken() {
            private volatile boolean cancelled = false;

            @Override
            public boolean isCancellationRequested() {
                return cancelled;
            }

            @Override
            public void cancel() {
                cancelled = true;
                if (cifrCancellation != null) {
                    cifrCancellation.run();
                }
            }
        };
    }
}
