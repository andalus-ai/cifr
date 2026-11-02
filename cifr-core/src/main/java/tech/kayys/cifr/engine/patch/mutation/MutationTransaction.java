package tech.kayys.cifr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.patch.PatchProposal;

public interface MutationTransaction {

    Uni<MutationTransaction> prepare(PatchProposal proposal);

    Uni<MutationResult> commit();

    Uni<Void> rollback();
}
