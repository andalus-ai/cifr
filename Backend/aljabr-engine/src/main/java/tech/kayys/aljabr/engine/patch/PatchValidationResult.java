package tech.kayys.aljabr.engine.patch;

import java.util.List;

public record PatchValidationResult(
    boolean isValid,
    List<String> errors
) {
    public static PatchValidationResult valid() {
        return new PatchValidationResult(true, List.of());
    }

    public static PatchValidationResult invalid(String error) {
        return new PatchValidationResult(false, List.of(error));
    }
    
    public static PatchValidationResult invalid(List<String> errors) {
        return new PatchValidationResult(false, List.copyOf(errors));
    }
}
