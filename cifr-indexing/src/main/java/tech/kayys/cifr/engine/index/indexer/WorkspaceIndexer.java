package tech.kayys.cifr.engine.index.indexer;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;

public interface WorkspaceIndexer {

    Uni<IndexResult> index(Path workspaceRoot, String workspaceId);

    Uni<Void> updateFile(Path file, Path workspaceRoot, String workspaceId);

    Uni<Void> removeFile(Path file, String workspaceId);

    /** Release the IDE process/session when a workspace is closed by its owner. */
    default Uni<Void> closeWorkspace(String workspaceId) {
        return Uni.createFrom().voidItem();
    }
}
