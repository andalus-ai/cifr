package tech.kayys.cifr.engine.debug;

import java.util.List;

public record Hypothesis(
    HypothesisId id,
    String statement,
    List<String> supportingEvidence,
    List<String> contradictingEvidence,
    double confidence,
    HypothesisStatus status
) {
    public Hypothesis {
        supportingEvidence = supportingEvidence != null ? List.copyOf(supportingEvidence) : List.of();
        contradictingEvidence = contradictingEvidence != null ? List.copyOf(contradictingEvidence) : List.of();
    }
}
