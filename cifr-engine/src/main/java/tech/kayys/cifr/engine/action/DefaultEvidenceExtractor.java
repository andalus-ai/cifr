package tech.kayys.cifr.engine.action;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.store.Evidence;
import tech.kayys.cifr.engine.store.EvidencePayload;
import tech.kayys.cifr.engine.store.EvidenceType;
import tech.kayys.cifr.engine.store.Provenance;
import tech.kayys.cifr.engine.task.TaskId;

import java.util.Map;

/**
 * Default implementation of {@link EvidenceExtractor}.
 */
@ApplicationScoped
public class DefaultEvidenceExtractor implements EvidenceExtractor {

    @Override
    public Evidence extract(String executionId, Observation observation) {
        if (observation == null) {
            return Evidence.create(
                "ev-" + executionId,
                EvidenceType.TOOL_RESULT,
                TaskId.of("system"),
                Provenance.of("executor", "1.0", executionId),
                EvidencePayload.structured("empty", Map.of("status", "empty"))
            );
        }

        EvidenceType type = observation.exitCode() == 0 ? EvidenceType.TOOL_RESULT : EvidenceType.TEST_RESULT;

        return Evidence.create(
            "ev-" + executionId,
            type,
            TaskId.of("system"),
            Provenance.of("executor", "1.0", executionId),
            EvidencePayload.structured("Observation exitCode " + observation.exitCode(), Map.of(
                "exitCode", observation.exitCode(),
                "output", observation.output() != null ? observation.output() : "",
                "error", observation.error() != null ? observation.error() : ""
            ))
        );
    }
}
