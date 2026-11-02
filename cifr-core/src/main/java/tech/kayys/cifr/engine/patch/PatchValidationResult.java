package tech.kayys.cifr.engine.patch;

import java.util.List;

public record PatchValidationResult(
    ValidationStatus status,
    List<PatchIssue> issues,
    List<PatchWarning> warnings
) {
    public PatchValidationResult {
        issues = issues != null ? List.copyOf(issues) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    public boolean isValid() {
        return status == ValidationStatus.VALID;
    }

    public List<String> errors() {
        return issues.stream()
            .map(PatchIssue::message)
            .toList();
    }

    public static PatchValidationResult valid() {
        return new PatchValidationResult(ValidationStatus.VALID, List.of(), List.of());
    }

    public static PatchValidationResult invalid(String error) {
        return new PatchValidationResult(
            ValidationStatus.INVALID,
            List.of(new PatchIssue(null, "VALIDATION_ERROR", error)),
            List.of()
        );
    }
    
    public static PatchValidationResult invalid(List<String> errors) {
        List<PatchIssue> issues = errors.stream()
            .map(err -> new PatchIssue(null, "VALIDATION_ERROR", err))
            .toList();
        return new PatchValidationResult(ValidationStatus.INVALID, issues, List.of());
    }
}
