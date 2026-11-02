package tech.kayys.cifr.engine.exec;

import java.time.Instant;

public record ExecutionLease(
    String leaseId,
    String resourceId,
    Instant expiresAt
) {}
