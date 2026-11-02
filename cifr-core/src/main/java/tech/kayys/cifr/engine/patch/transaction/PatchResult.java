package tech.kayys.cifr.engine.patch.transaction;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record PatchResult(
    PatchStatus status,
    Map<Path, String> modifiedFiles,
    int changedLinesCount,
    List<String> issues
) {
    public PatchResult {
        modifiedFiles = modifiedFiles != null ? Map.copyOf(modifiedFiles) : Map.of();
        issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public boolean isSuccess() {
        return status == PatchStatus.APPLIED;
    }
}
