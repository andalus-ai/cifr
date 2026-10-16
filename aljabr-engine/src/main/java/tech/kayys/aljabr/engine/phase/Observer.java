package tech.kayys.aljabr.engine.phase;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.api.agent.CodingRequest;

public interface Observer {
    Uni<ObservationResult> observe(CodingRequest request, ObservationContext context);
}
