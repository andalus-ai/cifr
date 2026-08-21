package tech.kayys.aljabr.engine.action;

import java.util.List;

/**
 * Structural validation result for an ActionProposal against its CapabilityContract.
 */
public record ValidationResult(
    boolean isValid,
    List<String> errors
) {
    public ValidationResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }
}
