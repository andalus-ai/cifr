package tech.kayys.cifr.engine.memory;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Rich metadata attributes for memory indexing and multi-signal retrieval.
 */
public record MemoryMetadata(
    Set<String> topics,
    Set<String> entities,
    Set<String> tags,
    Optional<String> sourceTaskId,
    Optional<String> sourceEvidenceId,
    Instant createdAt,
    Instant updatedAt
) {
    public MemoryMetadata {
        topics = topics != null ? Set.copyOf(topics) : Set.of();
        entities = entities != null ? Set.copyOf(entities) : Set.of();
        tags = tags != null ? Set.copyOf(tags) : Set.of();
    }

    public static MemoryMetadata create(Set<String> topics, Set<String> entities, String sourceTaskId, String sourceEvidenceId) {
        Instant now = Instant.now();
        return new MemoryMetadata(topics, entities, Set.of(), Optional.ofNullable(sourceTaskId), Optional.ofNullable(sourceEvidenceId), now, now);
    }
}
