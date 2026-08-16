package tech.kayys.aljabr.engine.plan;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface DynamicReplanner {

    Uni<AgentPlan> replanFailedStep(
        AgentPlan currentPlan,
        PlanStep failedStep,
        String failureReason,
        List<String> newEvidence
    );
}
