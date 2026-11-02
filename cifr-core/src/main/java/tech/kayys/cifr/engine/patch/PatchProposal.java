package tech.kayys.cifr.engine.patch;

import java.util.List;

public record PatchProposal(
    ChangePlanId planId,
    List<FilePatch> patches,
    List<String> supportingEvidence
) {
    public PatchProposal {
        patches = patches != null ? List.copyOf(patches) : List.of();
        supportingEvidence = supportingEvidence != null ? List.copyOf(supportingEvidence) : List.of();
    }
}
