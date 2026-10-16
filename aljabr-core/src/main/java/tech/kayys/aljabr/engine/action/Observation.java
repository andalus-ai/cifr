package tech.kayys.aljabr.engine.action;

import java.util.Map;

/**
 * Raw observation emitted by an action execution prior to evidence extraction.
 */
public record Observation(
    int exitCode,
    String output,
    String error,
    Map<String, Object> metadata
) {
    public Observation {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static Observation of(int exitCode, String output, String error) {
        return new Observation(exitCode, output, error, Map.of());
    }
}
