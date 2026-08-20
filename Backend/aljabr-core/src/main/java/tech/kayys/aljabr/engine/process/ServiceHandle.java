package tech.kayys.aljabr.engine.process;

public record ServiceHandle(
    ServiceId id,
    int port,
    ServiceStatus status
) {}
