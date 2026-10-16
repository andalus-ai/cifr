package tech.kayys.aljabr.engine.patch.transaction;

import java.util.List;

public record SemanticChangeIntent(
    String id,
    String targetSymbol,
    ChangeKind kind,
    String objective,
    List<String> constraints,
    double confidence
) {
    public SemanticChangeIntent {
        constraints = constraints != null ? List.copyOf(constraints) : List.of();
    }
}
