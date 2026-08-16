package tech.kayys.aljabr.engine.verification;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.index.WorkspaceFingerprint;
import tech.kayys.aljabr.engine.patch.mutation.EditProposal;

import java.nio.file.Path;

public interface VerificationEngine {

    Uni<VerificationResult> verify(WorkspaceFingerprint fingerprint, EditProposal proposal, VerificationLevel maxLevel, Path workspaceRoot);

    Uni<VerificationResult> runTargetedTests(String testTarget, Path workspaceRoot);
}
