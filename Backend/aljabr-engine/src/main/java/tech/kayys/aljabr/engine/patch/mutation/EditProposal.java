package tech.kayys.aljabr.engine.patch.mutation;

import java.util.List;

public record EditProposal(
    String objective,
    List<FileEdit> edits,
    List<String> invariants,
    List<String> supportingEvidence
) {
    public EditProposal {
        edits = edits != null ? List.copyOf(edits) : List.of();
        invariants = invariants != null ? List.copyOf(invariants) : List.of();
        supportingEvidence = supportingEvidence != null ? List.copyOf(supportingEvidence) : List.of();
    }
}
