package tech.kayys.cifr.engine.loop;

import java.util.List;

public record VerificationState(
    boolean passed,
    List<String> checkResults,
    List<String> diagnostics
) {
    public static VerificationState initial() {
        return new VerificationState(false, List.of(), List.of());
    }
}
