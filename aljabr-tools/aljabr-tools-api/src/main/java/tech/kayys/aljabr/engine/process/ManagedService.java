package tech.kayys.aljabr.engine.process;

import io.smallrye.mutiny.Uni;

public interface ManagedService {

    Uni<ServiceHandle> start(ServiceRequest request);

    Uni<ServiceStatus> status(ServiceId id);

    Uni<Void> stop(ServiceId id);
}
