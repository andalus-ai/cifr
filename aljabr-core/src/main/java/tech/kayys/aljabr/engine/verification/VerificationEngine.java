package tech.kayys.aljabr.engine.verification;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.index.WorkspaceFingerprint;
import tech.kayys.aljabr.engine.patch.mutation.EditProposal;

import java.nio.file.Path;

/**
 * Engine for performing multi-level closed-loop verification of workspace patches.
 */
public interface VerificationEngine {

    Uni<VerificationResult> verify(
        WorkspaceFingerprint fingerprint,
        EditProposal proposal,
        VerificationLevel level,
        Path workspaceRoot
    );
}
