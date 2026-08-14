package tech.kayys.aljabr.engine.cache;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultExecutionCache implements ExecutionCache {
    
    private static final Logger log = Logger.getLogger(DefaultExecutionCache.class.getName());
    
    // In-memory cache for MVP: stores CacheEntry mapped by the fully qualified string of CacheKey
    private final Map<String, CacheEntry> store = new ConcurrentHashMap<>();

    private record CacheEntry(Object value, Instant expiresAt) {}

    @Override
    public void invalidate(String sourceHash) {
        // Find and remove entries matching a source hash (namespace or inputHash component)
        store.keySet().removeIf(keyStr -> keyStr.contains(sourceHash));
        log.info("Invalidated cache entries related to " + sourceHash);
    }

    @Override
    public void invalidateByQuery(String query) {
        // Example: clear everything for a specific session or namespace
        store.keySet().removeIf(keyStr -> keyStr.contains(query));
        log.info("Invalidated cache entries matching query: " + query);
    }

    @Override
    public Optional<Object> get(CacheKey key, CachePolicy policy) {
        if (policy.allowBypass()) {
            return Optional.empty();
        }
        
        String keyStr = key.toString();
        CacheEntry entry = store.get(keyStr);
        if (entry == null) {
            return Optional.empty();
        }
        
        if (entry.expiresAt() != null && Instant.now().isAfter(entry.expiresAt())) {
            store.remove(keyStr);
            return Optional.empty();
        }
        
        // Handle negative caching (if value is a designated negative marker and policy disallows it, ignore it)
        if (!policy.allowNegativeCaching() && isNegativeMarker(entry.value())) {
            return Optional.empty();
        }
        
        return Optional.of(entry.value());
    }

    @Override
    public void put(CacheKey key, Object value, CachePolicy policy) {
        Instant expiresAt = null;
        if (policy.ttl() != null) {
            expiresAt = Instant.now().plus(policy.ttl());
        }
        
        store.put(key.toString(), new CacheEntry(value, expiresAt));
    }
    
    private boolean isNegativeMarker(Object value) {
        // e.g., if we cached an empty Optional or a specific error object
        if (value instanceof Optional<?> opt) {
            return opt.isEmpty();
        }
        return false;
    }
}
