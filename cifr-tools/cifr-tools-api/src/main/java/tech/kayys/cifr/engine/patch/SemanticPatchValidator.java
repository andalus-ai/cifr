package tech.kayys.cifr.engine.patch;

import io.smallrye.mutiny.Uni;

public interface SemanticPatchValidator {

    Uni<PatchValidationResult> validate(
        ChangePlan plan,
        PatchProposal patch
    );
}
