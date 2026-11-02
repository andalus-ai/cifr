package tech.kayys.cifr.engine.process;

public record ServiceHandle(
    ServiceId id,
    int port,
    ServiceStatus status
) {}
