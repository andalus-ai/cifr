package tech.kayys.aljabr.engine.orchestrator.output;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.exec.ExecutionResult;

public interface OutputProcessor {

    Uni<ProcessedOutput> process(String executionId, ExecutionResult result);
}
