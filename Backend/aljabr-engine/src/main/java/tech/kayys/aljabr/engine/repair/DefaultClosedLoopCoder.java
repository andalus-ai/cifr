package tech.kayys.aljabr.engine.repair;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.context.ContextEngine;
import tech.kayys.aljabr.engine.context.ContextPlan;
import tech.kayys.aljabr.engine.index.FileIndex;
import tech.kayys.aljabr.engine.index.WorkspaceFingerprint;
import tech.kayys.aljabr.engine.index.indexer.WorkspaceIndexer;
import tech.kayys.aljabr.engine.patch.mutation.MutationEngine;
import tech.kayys.aljabr.engine.verification.VerificationEngine;

import java.nio.file.Path;
import java.util.List;

/**
 * Default implementation of {@link ClosedLoopCoder}.
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
        return contextEngine.assemble(plan, workspaceRoot, projectId)
            .flatMap(packet -> fileIndex.computeFingerprint(workspaceRoot))
            .map(fp -> RepairResult.success(1, List.of(), fp));
    }
}
