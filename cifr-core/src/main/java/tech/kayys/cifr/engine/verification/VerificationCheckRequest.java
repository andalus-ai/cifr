package tech.kayys.cifr.engine.verification;

import tech.kayys.cifr.engine.orchestrator.policy.ExecutionBudget;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic verification command bound to one task and workspace revision.
 * The command must be selected by trusted Cifr policy, not directly by model output.
 */
public record VerificationCheckRequest(
        String taskId,
        String workspaceId,
        String workspaceFingerprint,
        String checkId,
        String verifierId,
        List<String> command,
        Path workingDirectory,
        Map<String, String> environment,
        ExecutionBudget budget) {

    public VerificationCheckRequest {
        requireText(taskId, "taskId");
        requireText(workspaceId, "workspaceId");
        requireText(workspaceFingerprint, "workspaceFingerprint");
        requireText(checkId, "checkId");
        requireText(verifierId, "verifierId");
        command = command == null ? List.of() : List.copyOf(command);
        if (command.isEmpty() || command.get(0) == null || command.get(0).isBlank()
                || command.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("command must contain a non-blank executable and no null arguments");
        }
        workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        budget = budget == null ? ExecutionBudget.defaultBudget() : budget;
        if (budget.timeout() == null || budget.timeout().isZero() || budget.timeout().isNegative()) {
            throw new IllegalArgumentException("verification timeout must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
