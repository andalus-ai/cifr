package tech.kayys.aljabr.engine.runtime.event;

import java.util.Optional;

/**
 * Metadata attached to runtime events establishing causation, correlation, and environment provenance.
 */
public record EventMetadata(
    String runtimeVersion,
    String modelId,
    Optional<String> modelVersion,
    String hostId,
    String correlationId,
    Optional<String> causationId
) {
    public static EventMetadata create(String correlationId, String causationId) {
        return new EventMetadata("1.0", "default-model", Optional.empty(), "local-node", correlationId, Optional.ofNullable(causationId));
    }
}
