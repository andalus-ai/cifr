package tech.kayys.cifr.engine.patch;

import java.nio.file.Path;
import java.util.List;

/**
 * Detailed diff information for a single modified file.
 */
public record FileDiff(
    Path path,
    int linesAdded,
    int linesRemoved,
    List<String> changedSymbols,
    boolean isNewFile,
    boolean isDeletedFile
) {
    public FileDiff {
        changedSymbols = changedSymbols != null ? List.copyOf(changedSymbols) : List.of();
    }
}
