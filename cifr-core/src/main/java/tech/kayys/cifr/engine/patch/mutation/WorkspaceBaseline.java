package tech.kayys.cifr.engine.patch.mutation;

import java.nio.file.Path;
import java.util.Map;
import tech.kayys.cifr.engine.index.WorkspaceFingerprint;

public record WorkspaceBaseline(
    WorkspaceFingerprint fingerprint,
    Map<Path, String> contentHashes,
    Map<String, String> gitState
) {
    public WorkspaceBaseline {
        contentHashes = contentHashes != null ? Map.copyOf(contentHashes) : Map.of();
        gitState = gitState != null ? Map.copyOf(gitState) : Map.of();
    }
}
