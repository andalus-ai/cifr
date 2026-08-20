package tech.kayys.aljabr.engine.index.indexer;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;

public interface WorkspaceIndexer {

    Uni<IndexResult> index(Path workspaceRoot, String workspaceId);

    Uni<Void> updateFile(Path file, Path workspaceRoot, String workspaceId);

    Uni<Void> removeFile(Path file, String workspaceId);
}
