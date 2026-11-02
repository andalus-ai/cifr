package tech.kayys.cifr.engine.debug;

import io.smallrye.mutiny.Uni;
import java.util.List;

public interface DebugEngine {

    Uni<DebugSession> startSession(String taskId, Failure failure);

    Uni<List<Hypothesis>> generateHypotheses(DebugSessionId sessionId, DebugContext context);

    Uni<ExperimentResult> executeExperiment(DebugSessionId sessionId, Experiment experiment);

    Uni<RepairPlan> concludeAndPlanRepair(DebugSessionId sessionId);
}
