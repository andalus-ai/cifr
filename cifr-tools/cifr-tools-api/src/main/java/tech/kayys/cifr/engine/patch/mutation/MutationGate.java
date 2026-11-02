package tech.kayys.cifr.engine.patch.mutation;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.api.agent.CifrState;
import tech.kayys.cifr.engine.patch.ChangeRisk;
import tech.kayys.cifr.engine.patch.PatchProposal;

public interface MutationGate {

    Uni<MutationAuthorization> authorize(
        PatchProposal proposal,
        ChangeRisk risk,
        CifrState state,
        MutationPolicy policy
    );
}
