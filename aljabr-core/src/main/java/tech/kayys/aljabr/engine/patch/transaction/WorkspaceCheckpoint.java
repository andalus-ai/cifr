package tech.kayys.aljabr.engine.patch.transaction;

import java.util.List;

/**
 * Checkpoint record capturing the original state of all files affected by a transaction.
 */
public record WorkspaceCheckpoint(
    String transactionId,
    String baseRevision,
    List<FileCheckpoint> files
) {
    public WorkspaceCheckpoint {
        files = files != null ? List.copyOf(files) : List.of();
    }

    public static WorkspaceCheckpoint of(String transactionId, String baseRevision, List<FileCheckpoint> files) {
        return new WorkspaceCheckpoint(transactionId, baseRevision, files);
    }
}
