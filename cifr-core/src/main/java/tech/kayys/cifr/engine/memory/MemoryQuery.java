package tech.kayys.cifr.engine.memory;

import tech.kayys.cifr.engine.store.MemoryScope;
import tech.kayys.cifr.engine.store.MemoryType;

import java.util.Optional;
import java.util.Set;

/**
 * Structured query for targeted memory retrieval across scope and type boundaries.
 */
public record MemoryQuery(
    String query,
    Set<MemoryScope> scopes,
    Set<MemoryType> types,
    Optional<String> taskId,
    Optional<String> projectId,
    int limit
) {
    public MemoryQuery {
        scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
        types = types != null ? Set.copyOf(types) : Set.of();
    }

    public static MemoryQuery of(String query, Set<MemoryScope> scopes, int limit) {
        return new MemoryQuery(query, scopes, Set.of(), Optional.empty(), Optional.empty(), limit);
    }
}
