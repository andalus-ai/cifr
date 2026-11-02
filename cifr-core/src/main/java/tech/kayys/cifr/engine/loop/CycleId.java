package tech.kayys.cifr.engine.loop;

import java.util.UUID;

/**
 * Unique identifier for a single decision cycle.
 */
public record CycleId(String value) {
    public static CycleId generate() {
        return new CycleId("cyc-" + UUID.randomUUID());
    }

    public static CycleId of(String value) {
        return new CycleId(value);
    }
}
