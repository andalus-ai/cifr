package tech.kayys.aljabr.engine.store;

import io.smallrye.mutiny.Uni;
import java.time.Duration;
import java.util.Optional;

/**
 * Disposable store for caching expensive computations (indexing, test runs, AST parsing).
 */
public interface CacheStore {

    <T> Uni<Optional<T>> get(CacheKey key, Class<T> type);

    Uni<Void> put(CacheKey key, Object value, Duration ttl);

    Uni<Void> invalidate(String operation);
}
