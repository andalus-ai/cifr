package tech.kayys.aljabr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.api.agent.AljabrState;
import tech.kayys.aljabr.engine.patch.ChangeRisk;
import tech.kayys.aljabr.engine.patch.PatchProposal;

public interface MutationGate {

    Uni<MutationAuthorization> authorize(
        PatchProposal proposal,
        ChangeRisk risk,
        AljabrState state,
        MutationPolicy policy
    );
}
