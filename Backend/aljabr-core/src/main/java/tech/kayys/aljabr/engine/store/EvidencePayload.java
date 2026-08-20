package tech.kayys.aljabr.engine.store;

import java.util.Map;
import java.util.Optional;

/**
 * Payload of an evidence record, separating concise summary from large raw data blobs.
 */
public record EvidencePayload(
    String summary,
    Map<String, Object> structuredData,
    Optional<String> rawDataRef
) {
    public EvidencePayload {
        structuredData = structuredData != null ? Map.copyOf(structuredData) : Map.of();
    }

    public static EvidencePayload text(String summary) {
        return new EvidencePayload(summary, Map.of(), Optional.empty());
    }

    public static EvidencePayload structured(String summary, Map<String, Object> data) {
        return new EvidencePayload(summary, data, Optional.empty());
    }
}
