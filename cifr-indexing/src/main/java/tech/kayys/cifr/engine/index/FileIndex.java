package tech.kayys.cifr.engine.index;

import io.smallrye.mutiny.Uni;
import java.nio.file.Path;
import java.util.List;

public interface FileIndex {
    Uni<WorkspaceFingerprint> computeFingerprint(Path workspaceRoot);
    Uni<List<FileRecord>> scanWorkspace(Path workspaceRoot);

    /** Scans and registers files under an explicit workspace identity. */
    default Uni<List<FileRecord>> scanWorkspace(Path workspaceRoot, String workspaceId) {
        return scanWorkspace(workspaceRoot);
    }

    Uni<List<FileRecord>> getCurrentIndex(String workspaceId);
    void registerFile(FileRecord record);
    void clear();

    /** Removes only one workspace's file records. */
    default void clearWorkspace(String workspaceId) {
        clear();
    }
}
