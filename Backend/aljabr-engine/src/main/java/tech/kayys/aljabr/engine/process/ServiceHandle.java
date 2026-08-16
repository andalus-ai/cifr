package tech.kayys.aljabr.engine.process;

import java.util.List;

public record ServiceHandle(
    ServiceId id,
    ProcessId processId,
    ServiceStatus status,
    List<Integer> boundPorts
) {
    public ServiceHandle {
        boundPorts = boundPorts != null ? List.copyOf(boundPorts) : List.of();
    }
}
