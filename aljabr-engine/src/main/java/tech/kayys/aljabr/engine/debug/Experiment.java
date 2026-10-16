package tech.kayys.aljabr.engine.debug;

import java.util.Map;

public record Experiment(
    ExperimentId id,
    HypothesisId hypothesisId,
    ExperimentKind kind,
    String description,
    Map<String, Object> parameters
) {
    public Experiment {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}
