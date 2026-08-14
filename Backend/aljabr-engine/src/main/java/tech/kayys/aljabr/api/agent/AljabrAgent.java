package tech.kayys.aljabr.api.agent;

import io.smallrye.mutiny.Uni;

public interface AljabrAgent {
    Uni<AljabrResult> run(CodingRequest request);
    Uni<AljabrState> state(String executionId);
    Uni<Void> cancel(String executionId);
}
