package tech.kayys.cifr.engine.cache;

import java.time.Duration;

public record CachePolicy(
    Duration ttl,
    boolean allowBypass,
    boolean allowNegativeCaching
) {}
