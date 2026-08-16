package tech.kayys.aljabr.engine.plan;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.loop.CodingTask;

public interface ControlLoop {

    Uni<PlanExecution> runLoop(CodingTask task, LoopBudget budget);
}
