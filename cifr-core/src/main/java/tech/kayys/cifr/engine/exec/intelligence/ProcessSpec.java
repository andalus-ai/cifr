package tech.kayys.cifr.engine.exec.intelligence;

import tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ProcessSpec(
    List<String> command,
    Path workingDirectory,
    Map<String, String> environment,
    ExecutionBudget budget
) {
    public ProcessSpec {
        command = command != null ? List.copyOf(command) : List.of();
        environment = environment != null ? Map.copyOf(environment) : Map.of();
    }
}
