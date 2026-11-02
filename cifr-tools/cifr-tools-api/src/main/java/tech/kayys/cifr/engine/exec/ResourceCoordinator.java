package tech.kayys.cifr.engine.exec;

import io.smallrye.mutiny.Uni;

public interface ResourceCoordinator {

    Uni<ExecutionLease> acquire(ExecutionCost cost);

    Uni<Void> release(ExecutionLease lease);
}
