package tech.kayys.cifr.engine.verification;

import java.util.List;

public record VerificationResult(
    boolean success,
    VerificationLevel level,
    int passedCount,
    int failedCount,
    long durationMs,
    List<TestFailureSummary> failures,
    String outputLog
) {
    public VerificationResult {
        failures = failures != null ? List.copyOf(failures) : List.of();
        outputLog = outputLog != null ? outputLog : "";
    }

    public static VerificationResult passed(VerificationLevel level, int passedCount, long durationMs) {
        return new VerificationResult(true, level, passedCount, 0, durationMs, List.of(), "Verification passed cleanly");
    }

    public static VerificationResult failed(VerificationLevel level, int passedCount, List<TestFailureSummary> failures, String outputLog, long durationMs) {
        int failedCount = failures != null ? failures.size() : 1;
        return new VerificationResult(false, level, passedCount, failedCount, durationMs, failures, outputLog);
    }
}
