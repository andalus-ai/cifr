package tech.kayys.aljabr.engine.patch;

import java.util.List;
import java.util.Set;

/**
 * High-level report summarizing all diffs in a proposed workspace mutation.
 */
public record DiffReport(
    List<FileDiff> files,
    Set<String> changedSymbols,
    boolean publicApiChanged,
    boolean testsChanged,
    RiskLevel risk
) {
    public DiffReport {
        files = files != null ? List.copyOf(files) : List.of();
        changedSymbols = changedSymbols != null ? Set.copyOf(changedSymbols) : Set.of();
    }

    public int totalFilesChanged() {
        return files.size();
    }
}
