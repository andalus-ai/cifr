package tech.kayys.cifr.engine.planning;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.lifecycle.AgentTask;

/**
 * Planning engine responsible for decomposing a goal into a progressive, resumable TaskGraph.
 */
public interface PlanningEngine {

    Uni<TaskGraph> plan(AgentTask task, String goalDescription);
}
