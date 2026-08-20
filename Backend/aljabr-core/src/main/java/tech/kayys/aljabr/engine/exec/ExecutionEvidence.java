package tech.kayys.aljabr.engine.exec;

import java.time.Instant;
import java.util.Map;

public record ExecutionEvidence(
    String evidenceId,
    String executionId,
    Instant timestamp,
    Map<String, Object> data
) {
    public ExecutionEvidence {
        data = data != null ? Map.copyOf(data) : Map.of();
    }
}
