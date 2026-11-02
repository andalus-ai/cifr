package tech.kayys.cifr.engine.task.checkpoint;

import tech.kayys.cifr.engine.task.TaskState;

public class DefaultPersistencePolicy implements PersistencePolicy {
    @Override
    public PersistenceDecision decide(String artifactType, ReproductionCost cost, TaskState state) {
        if (cost == null) {
            return PersistenceDecision.SKIP;
        }
        return switch (cost) {
            case TRIVIAL, CHEAP -> PersistenceDecision.SKIP;
            case MODERATE -> PersistenceDecision.CACHE;
            case EXPENSIVE, VERY_EXPENSIVE -> PersistenceDecision.PERSIST;
        };
    }
}
