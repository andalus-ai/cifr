package tech.kayys.cifr.engine.debug;

import java.nio.file.Path;
import java.util.List;
import tech.kayys.cifr.engine.index.test.TestCandidate;

public record DebugContext(
    Failure failure,
    List<Path> relevantFiles,
    List<String> relevantSymbols,
    List<String> recentChanges,
    List<TestCandidate> tests,
    List<String> evidenceIds
) {
    public DebugContext {
        relevantFiles = relevantFiles != null ? List.copyOf(relevantFiles) : List.of();
        relevantSymbols = relevantSymbols != null ? List.copyOf(relevantSymbols) : List.of();
        recentChanges = recentChanges != null ? List.copyOf(recentChanges) : List.of();
        tests = tests != null ? List.copyOf(tests) : List.of();
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }
}
