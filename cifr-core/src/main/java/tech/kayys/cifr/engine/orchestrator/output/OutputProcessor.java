package tech.kayys.cifr.engine.orchestrator.output;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.exec.ExecutionResult;

/**
 * Parses and processes raw execution output into structured diagnostics and summaries.
 */
public interface OutputProcessor {

    Uni<ProcessedOutput> process(String executionId, ExecutionResult result);
}
