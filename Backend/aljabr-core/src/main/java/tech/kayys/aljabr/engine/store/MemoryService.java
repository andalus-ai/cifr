package tech.kayys.aljabr.engine.store;

import io.smallrye.mutiny.Uni;
import java.util.List;

/**
 * Service managing storage, retrieval, and revocation (tombstoning) of memories across scopes.
 */
public interface MemoryService {

    Uni<Void> remember(Memory memory);

    Uni<Void> forget(String id);

    Uni<Void> forgetByScope(MemoryScope scope);

    Uni<List<Memory>> query(MemoryScope scope, MemoryType type);
}
