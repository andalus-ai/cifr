package tech.kayys.cifr.engine.index;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.List;

public interface FileIndex {
    Uni<WorkspaceFingerprint> computeFingerprint(Path workspaceRoot);
    Uni<List<FileRecord>> scanWorkspace(Path workspaceRoot);
    Uni<List<FileRecord>> getCurrentIndex(String workspaceId);
    void registerFile(FileRecord record);

    /** Remove one file without affecting another workspace that has the same relative path. */
    default void removeFile(Path file, String workspaceId) { }

    void clear();
}
