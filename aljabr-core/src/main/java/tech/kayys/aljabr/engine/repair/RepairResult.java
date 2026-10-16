package tech.kayys.aljabr.engine.repair;

import tech.kayys.aljabr.engine.index.WorkspaceFingerprint;

import java.util.List;

public record RepairResult(
    boolean success,
    int totalAttempts,
    List<RepairAttempt> attempts,
    WorkspaceFingerprint finalFingerprint,
    String message
) {
    public RepairResult {
        attempts = attempts != null ? List.copyOf(attempts) : List.of();
    }

    public static RepairResult success(int totalAttempts, List<RepairAttempt> attempts, WorkspaceFingerprint fingerprint) {
        return new RepairResult(true, totalAttempts, attempts, fingerprint, "Repair successfully verified");
    }

    public static RepairResult failed(int totalAttempts, List<RepairAttempt> attempts, String message) {
        return new RepairResult(false, totalAttempts, attempts, null, message);
    }
}
