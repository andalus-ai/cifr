package tech.kayys.cifr.engine.exec.intelligence;

import tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable process request. executionId lets callers cancel before completion. */
public record ProcessSpec(
    List<String> command,
    Path workingDirectory,
    Map<String, String> environment,
    ExecutionBudget budget,
    String executionId
) {
    public ProcessSpec(
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment,
            ExecutionBudget budget) {
        this(command, workingDirectory, environment, budget, UUID.randomUUID().toString());
    }

    public ProcessSpec {
        command = command != null ? List.copyOf(command) : List.of();
        environment = environment != null ? Map.copyOf(environment) : Map.of();
        executionId = executionId == null || executionId.isBlank()
                ? UUID.randomUUID().toString() : executionId;
    }
}
