package tech.kayys.cifr.engine.process;

import java.util.List;

public record ServiceRequest(
    ServiceId id,
    List<String> startCommand,
    int expectedPort
) {}
