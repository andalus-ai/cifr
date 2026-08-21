package tech.kayys.aljabr.engine.memory;

import tech.kayys.aljabr.engine.store.MemoryScope;
import java.util.Set;

/**
 * Resolves hierarchical scope visibility and inheritance boundaries.
 */
public interface MemoryScopeResolver {

    /**
     * Returns all memory scopes visible and queryable from the specified active scope.
     */
    Set<MemoryScope> resolveVisibleScopes(MemoryScope currentScope);

    /**
     * Checks if a target memory scope is accessible from the current querying scope.
     */
    boolean isAccessible(MemoryScope targetScope, MemoryScope currentScope);
}
