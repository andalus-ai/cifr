package tech.kayys.aljabr.engine.cache;

import java.util.Optional;

public interface ExecutionCache {
    void invalidate(String sourceHash);
    void invalidateByQuery(String query);
    
    Optional<Object> get(CacheKey key, CachePolicy policy);
    void put(CacheKey key, Object value, CachePolicy policy);
}
