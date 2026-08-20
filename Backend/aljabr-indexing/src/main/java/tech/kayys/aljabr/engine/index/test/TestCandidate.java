package tech.kayys.aljabr.engine.index.test;

import java.nio.file.Path;
import java.util.List;

public record TestCandidate(
    String selector,
    Path file,
    double relevance,
    List<String> reasons
) {
    public TestCandidate {
        reasons = reasons != null ? List.copyOf(reasons) : List.of();
    }
}
