package tech.kayys.cifr.engine.runtime.event;

import java.util.UUID;

/**
 * Strongly-typed unique identifier for a runtime event.
 */
public record EventId(String value) {
    public static EventId of(String value) {
        return new EventId(value);
    }

    public static EventId generate() {
        return new EventId("evt-" + UUID.randomUUID());
    }
}
