package tech.kayys.cifr.engine.process;

public record OutputPolicy(
    long maxOutputBytes,
    int maxLines,
    boolean streamLive
) {}
