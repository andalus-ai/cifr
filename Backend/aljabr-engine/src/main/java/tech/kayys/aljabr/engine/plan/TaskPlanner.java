package tech.kayys.aljabr.engine.plan;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.loop.CodingTask;

public interface TaskPlanner {

    Uni<AgentPlan> plan(CodingTask task);

    Uni<AgentPlan> replan(AgentPlan currentPlan, String failureReason);
}
