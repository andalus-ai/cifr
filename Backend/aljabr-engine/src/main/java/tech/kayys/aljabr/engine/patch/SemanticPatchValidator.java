package tech.kayys.aljabr.engine.patch;

import io.smallrye.mutiny.Uni;

public interface SemanticPatchValidator {

    Uni<PatchValidationResult> validate(
        ChangePlan plan,
        PatchProposal patch
    );
}
