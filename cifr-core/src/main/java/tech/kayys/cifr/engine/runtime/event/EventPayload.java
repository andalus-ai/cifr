package tech.kayys.cifr.engine.runtime.event;

/**
 * Marker interface for strongly-typed, versioned event payloads.
 */
public interface EventPayload {
    String payloadType();
}
