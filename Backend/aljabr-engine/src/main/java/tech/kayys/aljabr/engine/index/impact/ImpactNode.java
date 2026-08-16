package tech.kayys.aljabr.engine.index.impact;

import java.nio.file.Path;
import java.util.List;

public record ImpactNode(
    String identifier,
    String type,
    Path path,
    double impactScore,
    List<String> reasons
) {
    public ImpactNode {
        reasons = reasons != null ? List.copyOf(reasons) : List.of();
    }
}
