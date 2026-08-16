package tech.kayys.aljabr.engine.exec;

import java.time.Instant;
import java.util.UUID;

public record ExecutionLease(
    String leaseId,
    ExecutionCost allocatedCost,
    Instant acquiredAt
) {
    public static ExecutionLease create(ExecutionCost cost) {
        return new ExecutionLease("lease-" + UUID.randomUUID().toString().substring(0, 8), cost, Instant.now());
    }
}
