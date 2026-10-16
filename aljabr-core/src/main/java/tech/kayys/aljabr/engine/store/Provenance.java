package tech.kayys.aljabr.engine.store;

import java.time.Instant;
import java.util.Optional;

/**
 * Provenance tracking who produced the evidence, when, from what source, and with what tool version.
 */
public record Provenance(
    String producer,
    String producerVersion,
    Instant observedAt,
    Optional<String> derivedFromEvidenceId,
    Optional<String> sourceReference
) {
    public static Provenance of(String producer, String version, String sourceRef) {
        return new Provenance(producer, version, Instant.now(), Optional.empty(), Optional.ofNullable(sourceRef));
    }

    public static Provenance derived(String producer, String derivedFromId) {
        return new Provenance(producer, "1.0", Instant.now(), Optional.ofNullable(derivedFromId), Optional.empty());
    }
}
