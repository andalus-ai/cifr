package tech.kayys.aljabr.engine.process;

import io.smallrye.mutiny.Uni;

public interface ReadinessProbe {

    Uni<ReadinessResult> check(ServiceHandle service);
}
