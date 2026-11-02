package tech.kayys.cifr.engine.debug;

import java.util.List;
import java.util.Optional;

public record ExperimentResult(
    ExperimentId id,
    boolean succeeded,
    String observation,
    List<String> newEvidenceIds,
    Optional<HypothesisStatus> suggestedHypothesisStatus
) {
    public ExperimentResult {
        newEvidenceIds = newEvidenceIds != null ? List.copyOf(newEvidenceIds) : List.of();
    }
}
