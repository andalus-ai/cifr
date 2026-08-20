package tech.kayys.aljabr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;

public interface MutationEngine {

    Uni<MutationResult> apply(EditProposal proposal, Path workspaceRoot);

    Uni<Void> rollback(MutationId mutationId, Path workspaceRoot);
}
