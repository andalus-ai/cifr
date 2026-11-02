package tech.kayys.cifr.engine.index;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface FileIndex {
    Uni<WorkspaceFingerprint> computeFingerprint(Path workspaceRoot);
    Uni<List<FileRecord>> scanWorkspace(Path workspaceRoot);
    Uni<List<FileRecord>> getCurrentIndex(String workspaceId);
    void registerFile(FileRecord record);

    /** Remove one file without affecting another workspace that has the same relative path. */
    default void removeFile(Path file, String workspaceId) { }

    /** Remove indexed records absent from a completed workspace scan. */
    default List<Path> removeMissingFiles(String workspaceId, Set<Path> presentFiles) {
        return List.of();
    }

    void clear();
}
