package tech.kayys.cifr.engine.phase;

import java.util.List;

public record ObservationResult(
    Object repository,
    List<Object> observations,
    List<String> evidenceIds,
    List<Unknown> unknowns
) {}
