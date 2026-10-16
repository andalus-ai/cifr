package tech.kayys.aljabr.engine.task.checkpoint;

import tech.kayys.aljabr.engine.task.TaskState;

public interface PersistencePolicy {
    PersistenceDecision decide(String artifactType, ReproductionCost cost, TaskState state);
}
