package tech.kayys.aljabr.engine.verification.graph;

import java.util.List;

public record VerificationEvidence(
    String evidenceId,
    String description,
    List<String> rawArtifacts
) {
    public VerificationEvidence {
        rawArtifacts = rawArtifacts != null ? List.copyOf(rawArtifacts) : List.of();
    }
}
