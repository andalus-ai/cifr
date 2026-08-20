package tech.kayys.aljabr.engine.exec;

import java.time.Instant;

public record ExecutionLease(
    String leaseId,
    String resourceId,
    Instant expiresAt
) {}
