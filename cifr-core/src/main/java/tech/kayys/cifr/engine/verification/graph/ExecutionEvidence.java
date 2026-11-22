package tech.kayys.cifr.engine.verification.graph;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable provenance record for one executed verification check.
 *
 * <p>Unlike descriptive {@link VerificationEvidence}, this record binds a result
 * to a task, workspace and exact workspace fingerprint. A passing result is only
 * valid when the process exit code is zero and the execution interval is valid.</p>
 */
public record ExecutionEvidence(
        String evidenceId,
        String taskId,
        String workspaceId,
        String workspaceFingerprint,
        String checkId,
        String verifierId,
        List<String> command,
        ExecutionCheckStatus status,
        Integer exitCode,
        String stdoutArtifactRef,
        String stderrArtifactRef,
        Instant startedAt,
        Instant completedAt) {

    public ExecutionEvidence {
        requireText(evidenceId, "evidenceId");
        requireText(taskId, "taskId");
        requireText(workspaceId, "workspaceId");
        requireText(workspaceFingerprint, "workspaceFingerprint");
        requireText(checkId, "checkId");
        requireText(verifierId, "verifierId");
        Objects.requireNonNull(status, "status");
        command = command == null ? List.of() : List.copyOf(command);
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not precede startedAt");
        }
        if (status == ExecutionCheckStatus.PASSED && !Integer.valueOf(0).equals(exitCode)) {
            throw new IllegalArgumentException("A passing execution must have exit code 0");
        }
    }

    public boolean provesPassFor(
            String expectedTaskId,
            String expectedWorkspaceId,
            String expectedWorkspaceFingerprint,
            String expectedCheckId) {
        return status == ExecutionCheckStatus.PASSED
                && Integer.valueOf(0).equals(exitCode)
                && taskId.equals(expectedTaskId)
                && workspaceId.equals(expectedWorkspaceId)
                && workspaceFingerprint.equals(expectedWorkspaceFingerprint)
                && checkId.equals(expectedCheckId)
                && !completedAt.isBefore(startedAt);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
