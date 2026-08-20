package tech.kayys.aljabr.engine.patch.mutation;

import java.util.List;
import tech.kayys.aljabr.engine.index.WorkspaceFingerprint;
import tech.kayys.aljabr.engine.repo.ChangeSet;

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
