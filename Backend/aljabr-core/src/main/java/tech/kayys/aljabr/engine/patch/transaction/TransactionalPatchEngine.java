package tech.kayys.aljabr.engine.patch.transaction;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.List;

public interface TransactionalPatchEngine {

    Uni<PatchResult> applyTransactional(
        SemanticChangeIntent intent,
        List<EditOperation> operations,
        Path workspaceRoot,
        ChangeBudget budget
    );
}
