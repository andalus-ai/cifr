package tech.kayys.cifr.engine.store;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default in-memory implementation of {@link MemoryService}.
 */
@ApplicationScoped
public class DefaultMemoryService implements MemoryService {

    private final Map<String, Memory> memoryStore = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> remember(Memory memory) {
        if (memory != null) {
            memoryStore.put(memory.id(), memory);
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> forget(String id) {
        if (id != null) {
            Memory existing = memoryStore.get(id);
            if (existing != null) {
                memoryStore.put(id, existing.revoke());
            }
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> forgetByScope(MemoryScope scope) {
        if (scope != null) {
            for (Map.Entry<String, Memory> entry : memoryStore.entrySet()) {
                if (entry.getValue().scope() == scope) {
                    memoryStore.put(entry.getKey(), entry.getValue().revoke());
                }
            }
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<List<Memory>> query(MemoryScope scope, MemoryType type) {
        return Uni.createFrom().item(() ->
            memoryStore.values().stream()
                .filter(m -> m.lifecycle() == MemoryLifecycle.ACTIVE)
                .filter(m -> scope == null || m.scope() == scope)
                .filter(m -> type == null || m.type() == type)
                .collect(Collectors.toList())
        );
    }
}
