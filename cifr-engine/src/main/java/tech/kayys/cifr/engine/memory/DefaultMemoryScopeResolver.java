package tech.kayys.cifr.engine.memory;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.store.MemoryScope;

import java.util.EnumSet;
import java.util.Set;

/**
 * Default hierarchical scope resolver.
 * Scopes widen monotonically: INVOCATION -> TASK -> SESSION -> WORKSPACE -> PROJECT -> USER -> GLOBAL.
 */
@ApplicationScoped
public class DefaultMemoryScopeResolver implements MemoryScopeResolver {

    @Override
    public Set<MemoryScope> resolveVisibleScopes(MemoryScope currentScope) {
        if (currentScope == null) {
            return EnumSet.of(MemoryScope.GLOBAL);
        }
        return switch (currentScope) {
            case INVOCATION -> EnumSet.of(
                MemoryScope.INVOCATION, MemoryScope.TASK, MemoryScope.SESSION,
                MemoryScope.WORKSPACE, MemoryScope.PROJECT, MemoryScope.USER, MemoryScope.GLOBAL
            );
            case TASK -> EnumSet.of(
                MemoryScope.TASK, MemoryScope.SESSION, MemoryScope.WORKSPACE,
                MemoryScope.PROJECT, MemoryScope.USER, MemoryScope.GLOBAL
            );
            case SESSION -> EnumSet.of(
                MemoryScope.SESSION, MemoryScope.WORKSPACE, MemoryScope.PROJECT,
                MemoryScope.USER, MemoryScope.GLOBAL
            );
            case WORKSPACE -> EnumSet.of(
                MemoryScope.WORKSPACE, MemoryScope.PROJECT, MemoryScope.USER, MemoryScope.GLOBAL
            );
            case PROJECT -> EnumSet.of(
                MemoryScope.PROJECT, MemoryScope.USER, MemoryScope.GLOBAL
            );
            case USER -> EnumSet.of(
                MemoryScope.USER, MemoryScope.GLOBAL
            );
            case GLOBAL -> EnumSet.of(MemoryScope.GLOBAL);
        };
    }

    @Override
    public boolean isAccessible(MemoryScope targetScope, MemoryScope currentScope) {
        if (targetScope == null || currentScope == null) {
            return false;
        }
        return resolveVisibleScopes(currentScope).contains(targetScope);
    }
}
