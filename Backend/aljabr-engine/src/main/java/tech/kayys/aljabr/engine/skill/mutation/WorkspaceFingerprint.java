package tech.kayys.aljabr.engine.skill.mutation;

import java.nio.file.Path;
import java.util.Map;

public record WorkspaceFingerprint(
    String repositoryHash,
    Map<Path, String> fileHashes
) {
    public WorkspaceFingerprint {
        fileHashes = fileHashes != null ? Map.copyOf(fileHashes) : Map.of();
    }
}
