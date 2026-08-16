package tech.kayys.aljabr.engine.orchestrator.exec;

import tech.kayys.aljabr.engine.orchestrator.output.ExecutionStatus;
import tech.kayys.aljabr.engine.orchestrator.output.ProcessedOutput;

import java.time.Duration;

public record ExecutionResult(
    String executionId,
    ExecutionStatus status,
    int exitCode,
    Duration duration,
    ProcessedOutput output
) {
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS && exitCode == 0;
    }
}
