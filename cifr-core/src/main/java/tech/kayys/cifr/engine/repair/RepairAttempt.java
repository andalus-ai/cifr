package tech.kayys.cifr.engine.repair;

import tech.kayys.cifr.engine.patch.mutation.EditProposal;
import tech.kayys.cifr.engine.verification.TestFailureSummary;
import tech.kayys.cifr.engine.verification.VerificationResult;

import java.util.List;

public record RepairAttempt(
    int attemptNumber,
    List<TestFailureSummary> failureSummary,
    String hypothesis,
    EditProposal proposal,
    VerificationResult verificationResult
) {
    public RepairAttempt {
        failureSummary = failureSummary != null ? List.copyOf(failureSummary) : List.of();
    }
}
