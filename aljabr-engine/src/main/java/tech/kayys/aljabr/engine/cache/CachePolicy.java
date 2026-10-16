package tech.kayys.aljabr.engine.cache;

import java.time.Duration;

public record CachePolicy(
    Duration ttl,
    boolean allowBypass,
    boolean allowNegativeCaching
) {}
