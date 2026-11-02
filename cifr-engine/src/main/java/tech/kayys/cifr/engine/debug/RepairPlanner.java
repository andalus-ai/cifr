package tech.kayys.cifr.engine.debug;

import io.smallrye.mutiny.Uni;

public interface RepairPlanner {

    Uni<RepairPlan> planRepair(Hypothesis confirmedHypothesis, DebugContext context);
}
