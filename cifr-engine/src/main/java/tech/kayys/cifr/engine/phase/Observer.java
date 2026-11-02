package tech.kayys.cifr.engine.phase;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.api.agent.CodingRequest;

public interface Observer {
    Uni<ObservationResult> observe(CodingRequest request, ObservationContext context);
}
