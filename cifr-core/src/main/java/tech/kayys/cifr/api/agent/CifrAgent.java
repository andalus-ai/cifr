package tech.kayys.cifr.api.agent;

import io.smallrye.mutiny.Uni;

public interface CifrAgent {
    Uni<CifrResult> run(CodingRequest request);
    Uni<CifrState> state(String executionId);
    Uni<Void> cancel(String executionId);
}
