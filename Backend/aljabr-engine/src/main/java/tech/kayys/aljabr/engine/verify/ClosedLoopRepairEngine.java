package tech.kayys.aljabr.engine.verify;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.debug.DebugSession;
import tech.kayys.aljabr.engine.debug.Failure;
import tech.kayys.aljabr.engine.patch.mutation.MutationResult;

public interface ClosedLoopRepairEngine {

    Uni<MutationResult> repairAndVerify(
        String taskId,
        Failure failure,
        VerificationStrategy strategy
    );

    Uni<DebugSession> currentDebugSession(String taskId);
}
