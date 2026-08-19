package tech.kayys.aljabr.engine.patch.transaction;

import java.util.List;

public record PreconditionValidation(
    boolean isValid,
    List<String> errors,
    List<String> warnings
) {
    public PreconditionValidation {
        errors = errors != null ? List.copyOf(errors) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    public static PreconditionValidation valid() {
        return new PreconditionValidation(true, List.of(), List.of());
    }

    public static PreconditionValidation failed(String error) {
        return new PreconditionValidation(false, List.of(error), List.of());
    }
}
