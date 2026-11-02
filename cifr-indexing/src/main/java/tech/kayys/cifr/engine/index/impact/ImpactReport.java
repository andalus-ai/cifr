package tech.kayys.cifr.engine.index.impact;

import java.nio.file.Path;
import java.util.List;

public record ImpactReport(
    List<ImpactNode> affectedSymbols,
    List<ImpactNode> affectedFiles,
    List<Path> likelyTests,
    ImpactConfidence confidence
) {
    public ImpactReport {
        affectedSymbols = affectedSymbols != null ? List.copyOf(affectedSymbols) : List.of();
        affectedFiles = affectedFiles != null ? List.copyOf(affectedFiles) : List.of();
        likelyTests = likelyTests != null ? List.copyOf(likelyTests) : List.of();
    }
}
