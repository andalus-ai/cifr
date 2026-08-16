package tech.kayys.aljabr.engine.exec;

import io.smallrye.mutiny.Uni;

public interface ResourceCoordinator {

    Uni<ExecutionLease> acquire(ExecutionCost cost);

    Uni<Void> release(ExecutionLease lease);
}
