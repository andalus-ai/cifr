package tech.kayys.cifr.engine.verification;

import java.util.List;
import java.util.Objects;

/**
 * A trusted, deterministic command definition for one verification check.
 * Commands are argv vectors; callers must not reinterpret them as shell text.
 */
public record VerificationCheckDefinition(
        String checkId,
        String verifierId,
        List<String> command) {

    public VerificationCheckDefinition {
        if (checkId == null || checkId.isBlank()) {
            throw new IllegalArgumentException("checkId must not be blank");
        }
        if (verifierId == null || verifierId.isBlank()) {
            throw new IllegalArgumentException("verifierId must not be blank");
        }
        command = command == null ? List.of() : List.copyOf(command);
        if (command.isEmpty() || command.get(0) == null || command.get(0).isBlank()
                || command.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("command must contain a non-blank executable and no null arguments");
        }
    }
}
