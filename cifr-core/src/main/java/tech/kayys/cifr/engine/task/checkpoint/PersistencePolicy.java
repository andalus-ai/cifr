package tech.kayys.cifr.engine.task.checkpoint;

import tech.kayys.cifr.engine.task.TaskState;

public interface PersistencePolicy {
    PersistenceDecision decide(String artifactType, ReproductionCost cost, TaskState state);
}
