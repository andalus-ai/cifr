package tech.kayys.cifr.engine.verification.graph;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceCompletionPolicyTest {

    private static final Instant START = Instant.parse("2026-11-21T07:00:00Z");
    private static final Instant END = Instant.parse("2026-11-21T07:00:02Z");
    private static final String TASK = "task-1";
    private static final String WORKSPACE = "workspace-1";
    private static final String FINGERPRINT = "sha256:revision-a";
    private final EvidenceCompletionPolicy policy = new EvidenceCompletionPolicy();

    @Test
    void allRequiredChecksMustPassForExactTaskAndRevision() {
        assertEquals(CompletionDecision.COMPLETE,
                policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of("compile", "tests"),
                        List.of(passed("compile"), passed("tests"))));
    }

    @Test
    void missingEvidenceDoesNotComplete() {
        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of("compile"), List.of()));
    }

    @Test
    void evidenceFromAnotherTaskCannotComplete() {
        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of("compile"),
                        List.of(evidence("compile", "another-task", WORKSPACE, FINGERPRINT,
                                ExecutionCheckStatus.PASSED, 0))));
    }

    @Test
    void evidenceFromAnotherWorkspaceCannotComplete() {
        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of("compile"),
                        List.of(evidence("compile", TASK, "other-workspace", FINGERPRINT,
                                ExecutionCheckStatus.PASSED, 0))));
    }

    @Test
    void staleWorkspaceFingerprintCannotComplete() {
        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of("compile"),
                        List.of(evidence("compile", TASK, WORKSPACE, "sha256:old",
                                ExecutionCheckStatus.PASSED, 0))));
    }

    @Test
    void failedCheckRequiresRepair() {
        assertEquals(CompletionDecision.NEED_REPAIR,
                policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of("compile"),
                        List.of(evidence("compile", TASK, WORKSPACE, FINGERPRINT,
                                ExecutionCheckStatus.FAILED, 1))));
    }

    @Test
    void skippedOrNotRunCheckIsNotSuccess() {
        for (ExecutionCheckStatus status : List.of(
                ExecutionCheckStatus.SKIPPED, ExecutionCheckStatus.NOT_RUN, ExecutionCheckStatus.ERROR)) {
            assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                    policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of("compile"),
                            List.of(evidence("compile", TASK, WORKSPACE, FINGERPRINT, status, null))));
        }
    }

    @Test
    void duplicateResultsForRequiredCheckAreBlocked() {
        assertEquals(CompletionDecision.BLOCKED,
                policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of("compile"),
                        List.of(passed("compile"), passed("compile"))));
    }

    @Test
    void missingPolicyIdentityOrRequirementsCannotComplete() {
        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(TASK, WORKSPACE, FINGERPRINT, Set.of(), List.of(passed("compile"))));
        assertEquals(CompletionDecision.NEED_MORE_VERIFICATION,
                policy.evaluate(null, WORKSPACE, FINGERPRINT, Set.of("compile"), List.of(passed("compile"))));
    }

    @Test
    void passingEvidenceRequiresZeroExitCodeAndValidInterval() {
        assertThrows(IllegalArgumentException.class, () -> evidence(
                "compile", TASK, WORKSPACE, FINGERPRINT, ExecutionCheckStatus.PASSED, 1));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionEvidence(
                "ev-invalid", TASK, WORKSPACE, FINGERPRINT, "compile", "maven",
                List.of("mvn", "test"), ExecutionCheckStatus.PASSED, 0,
                null, null, END, START));
    }

    private static ExecutionEvidence passed(String checkId) {
        return evidence(checkId, TASK, WORKSPACE, FINGERPRINT, ExecutionCheckStatus.PASSED, 0);
    }

    private static ExecutionEvidence evidence(
            String checkId, String taskId, String workspaceId, String fingerprint,
            ExecutionCheckStatus status, Integer exitCode) {
        return new ExecutionEvidence(
                "ev-" + checkId + "-" + taskId, taskId, workspaceId, fingerprint,
                checkId, "test-provider", List.of("mvn", "verify"), status, exitCode,
                "artifact://stdout/" + checkId, "artifact://stderr/" + checkId, START, END);
    }
}
