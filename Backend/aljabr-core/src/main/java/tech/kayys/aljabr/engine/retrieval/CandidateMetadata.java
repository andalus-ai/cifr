package tech.kayys.aljabr.engine.retrieval;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Metadata for a retrieved candidate object.
 */
public record CandidateMetadata(
    Optional<String> title,
    Set<String> entities,
    Set<String> topics,
    Instant createdAt,
    Optional<Instant> verifiedAt,
    double confidence
) {
    public CandidateMetadata {
        entities = entities != null ? Set.copyOf(entities) : Set.of();
        topics = topics != null ? Set.copyOf(topics) : Set.of();
    }

    public static CandidateMetadata of(String title, Set<String> entities, double confidence) {
        return new CandidateMetadata(Optional.ofNullable(title), entities, Set.of(), Instant.now(), Optional.empty(), confidence);
    }
}
