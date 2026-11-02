package tech.kayys.cifr.engine.patch.mutation;

import java.util.List;
import tech.kayys.cifr.engine.index.WorkspaceFingerprint;
import tech.kayys.cifr.engine.repo.ChangeSet;

public record MutationResult(
    MutationId id,
    ChangeSet changes,
    List<String> evidenceIds,
    WorkspaceFingerprint newFingerprint
) {
    public MutationResult {
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }
}
