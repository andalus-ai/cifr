package tech.kayys.cifr.engine.index;

import java.nio.file.Path;

/**
 * File metadata scoped to the workspace that owns the file.
 *
 * <p>The workspace identity is required by IDE-backed services: relative paths
 * are not globally unique and must never be resolved against an implicit
 * "default" workspace.</p>
 */
public record FileRecord(
    Path path,
    String contentHash,
    long size,
    String language,
    boolean generated,
    boolean test,
    FileRole role,
    String workspaceId
) {
    public FileRecord {
        if (workspaceId == null || workspaceId.isBlank()) {
            workspaceId = "default-workspace";
        }
    }

    /**
     * Compatibility constructor for callers that have not yet been migrated
     * to explicit workspace scoping.
     */
    public FileRecord(
        Path path,
        String contentHash,
        long size,
        String language,
        boolean generated,
        boolean test,
        FileRole role
    ) {
        this(path, contentHash, size, language, generated, test, role, "default-workspace");
    }
}
