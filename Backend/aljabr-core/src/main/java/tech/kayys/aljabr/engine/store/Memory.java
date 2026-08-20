package tech.kayys.aljabr.engine.store;

import java.time.Instant;

/**
 * Retained piece of knowledge with explicit scope, type, provenance, and lifecycle.
 */
public record Memory(
    String id,
    MemoryScope scope,
    MemoryType type,
    String content,
    double importance,
    Provenance provenance,
    MemoryLifecycle lifecycle,
    Instant createdAt,
    Instant updatedAt
) {
    public static Memory create(String id, MemoryScope scope, MemoryType type, String content, double importance, Provenance provenance) {
        Instant now = Instant.now();
        return new Memory(id, scope, type, content, importance, provenance, MemoryLifecycle.ACTIVE, now, now);
    }

    public Memory revoke() {
        return new Memory(id, scope, type, content, importance, provenance, MemoryLifecycle.REVOKED, createdAt, Instant.now());
    }
}
