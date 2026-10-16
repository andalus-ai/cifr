package tech.kayys.aljabr.engine.process;

public record OutputPolicy(
    long maxOutputBytes,
    int maxLines,
    boolean streamLive
) {}
