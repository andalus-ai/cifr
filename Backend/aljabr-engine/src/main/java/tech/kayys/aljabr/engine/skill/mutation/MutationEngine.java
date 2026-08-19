package tech.kayys.aljabr.engine.skill.mutation;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.patch.mutation.MutationId;
import tech.kayys.aljabr.engine.patch.mutation.MutationResult;

public interface MutationEngine {

    Uni<MutationResult> apply(EditProposal proposal);

    Uni<Void> rollback(MutationId mutation);
}
