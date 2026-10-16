package tech.kayys.aljabr.engine.orchestrator.output;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.exec.ExecutionResult;

/**
 * Parses and processes raw execution output into structured diagnostics and summaries.
 */
public interface OutputProcessor {

    Uni<ProcessedOutput> process(String executionId, ExecutionResult result);
}
