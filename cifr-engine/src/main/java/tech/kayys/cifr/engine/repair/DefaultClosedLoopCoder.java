package tech.kayys.cifr.engine.repair;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.context.ContextEngine;
import tech.kayys.cifr.engine.context.ContextPlan;
import tech.kayys.cifr.engine.index.FileIndex;
import tech.kayys.cifr.engine.patch.mutation.MutationEngine;
import tech.kayys.cifr.engine.verification.VerificationEngine;
import tech.kayys.cifr.engine.index.indexer.WorkspaceIndexer;

import java.nio.file.Path;
import java.util.List;

/**
 * Default closed-loop repair entry point.
 *
 * <p>This implementation currently has no model/planner input capable of
 * producing an {@code EditProposal}. It must therefore fail explicitly rather
 * than claim a repair was applied and verified.</p>
 */
@ApplicationScoped
public class DefaultClosedLoopCoder implements ClosedLoopCoder {

    @Inject
    ContextEngine contextEngine;

    @Inject
    MutationEngine mutationEngine;

    @Inject
    VerificationEngine verificationEngine;

    @Inject
    WorkspaceIndexer workspaceIndexer;

    @Inject
    FileIndex fileIndex;

    public DefaultClosedLoopCoder() {}

    public DefaultClosedLoopCoder(
        ContextEngine contextEngine,
        MutationEngine mutationEngine,
        VerificationEngine verificationEngine,
        WorkspaceIndexer workspaceIndexer,
        FileIndex fileIndex
    ) {
        this.contextEngine = contextEngine;
        this.mutationEngine = mutationEngine;
        this.verificationEngine = verificationEngine;
        this.workspaceIndexer = workspaceIndexer;
        this.fileIndex = fileIndex;
    }

    @Override
    public Uni<RepairResult> executeLoop(
        ContextPlan plan,
        int maxAttempts,
        Path workspaceRoot,
        String projectId
    ) {
        return Uni.createFrom().item(RepairResult.failed(
                0,
                List.of(),
                "Repair was not executed: no edit proposal or repair strategy is available. " +
                "Refusing to report success without mutation and verification."));
    }
}
