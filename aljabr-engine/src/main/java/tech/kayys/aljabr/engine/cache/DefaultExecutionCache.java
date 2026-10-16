package tech.kayys.aljabr.engine.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultExecutionCache implements ExecutionCache {
    
    private static final Logger log = Logger.getLogger(DefaultExecutionCache.class.getName());
    
    private final ReactiveValueCommands<String, String> valueCommands;
    private final ReactiveKeyCommands<String> keyCommands;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public DefaultExecutionCache(ReactiveRedisDataSource ds) {
        this.valueCommands = ds.value(String.class);
        this.keyCommands = ds.key();
    }

    private record CacheEntry(String valueJson, Instant expiresAt) {}

    @Override
    public void invalidate(String sourceHash) {
        // Find keys via KEYS pattern (not recommended for prod but okay for MVP caching logic)
        keyCommands.keys("*" + sourceHash + "*").subscribe().with(keys -> {
            if (keys != null && !keys.isEmpty()) {
                keyCommands.del(keys.toArray(new String[0])).subscribe().with(
                    count -> log.info("Invalidated " + count + " cache entries related to " + sourceHash)
                );
            }
        });
    }

    @Override
    public void invalidateByQuery(String query) {
        keyCommands.keys("*" + query + "*").subscribe().with(keys -> {
            if (keys != null && !keys.isEmpty()) {
                keyCommands.del(keys.toArray(new String[0])).subscribe().with(
                    count -> log.info("Invalidated " + count + " cache entries matching query: " + query)
                );
            }
        });
    }

    @Override
    public Optional<Object> get(CacheKey key, CachePolicy policy) {
        if (policy.allowBypass()) {
            return Optional.empty();
        }
        
        String keyStr = key.toString();
        // Since we are synchronous on the interface but Redis is reactive, 
        // we might block for the cache retrieval, or since it's Optional<Object>, we block.
        // For MVP, blocking is acceptable if the interface requires it.
        try {
            String json = valueCommands.get(keyStr).await().indefinitely();
            if (json == null) {
                return Optional.empty();
            }
            CacheEntry entry = mapper.readValue(json, CacheEntry.class);
            
            if (entry.expiresAt() != null && Instant.now().isAfter(entry.expiresAt())) {
                keyCommands.del(keyStr).subscribe().with(c -> {});
                return Optional.empty();
            }
            
            Object parsedValue = mapper.readValue(entry.valueJson(), Object.class);
            if (!policy.allowNegativeCaching() && isNegativeMarker(parsedValue)) {
                return Optional.empty();
            }
            return Optional.ofNullable(parsedValue);
            
        } catch (Exception e) {
            log.warning("Cache retrieval failed for key: " + keyStr + " " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(CacheKey key, Object value, CachePolicy policy) {
        Instant expiresAt = null;
        if (policy.ttl() != null) {
            expiresAt = Instant.now().plus(policy.ttl());
        }
        try {
            String valueJson = mapper.writeValueAsString(value);
            CacheEntry entry = new CacheEntry(valueJson, expiresAt);
            String entryJson = mapper.writeValueAsString(entry);
            
            if (policy.ttl() != null) {
                valueCommands.setex(key.toString(), policy.ttl().getSeconds(), entryJson).subscribe().with(r -> {});
            } else {
                valueCommands.set(key.toString(), entryJson).subscribe().with(r -> {});
            }
        } catch (JsonProcessingException e) {
            log.warning("Cache put failed due to serialization error for key: " + key.toString());
        }
    }
    
    private boolean isNegativeMarker(Object value) {
        if (value instanceof Optional<?> opt) {
            return opt.isEmpty();
        }
        return false;
    }
}
