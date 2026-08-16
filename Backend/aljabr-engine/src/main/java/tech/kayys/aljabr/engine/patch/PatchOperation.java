package tech.kayys.aljabr.engine.patch;

import java.util.List;
import tech.kayys.aljabr.engine.repo.RepositorySnapshot;

public record PatchOperation(
    String id,
    String path,
    List<PatchEdit> edits,
    String reason,
    String expectedCommit,
    RepositorySnapshot expectedSnapshot
) {
    public PatchOperation {
        edits = List.copyOf(edits);
    }
}
