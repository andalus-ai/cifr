package tech.kayys.aljabr.engine.cache;

public record CacheKey(
    String tenant,
    String session,
    String executionId,
    String namespace,
    String inputHash
) {}
