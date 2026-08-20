package tech.kayys.aljabr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.patch.PatchProposal;

public interface MutationTransaction {

    Uni<MutationTransaction> prepare(PatchProposal proposal);

    Uni<MutationResult> commit();

    Uni<Void> rollback();
}
