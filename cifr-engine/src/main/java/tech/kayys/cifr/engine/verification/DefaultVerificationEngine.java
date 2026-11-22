package tech.kayys.cifr.engine.verification;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.index.WorkspaceFingerprint;
import tech.kayys.cifr.engine.patch.mutation.EditProposal;

import java.nio.file.Path;
import java.util.List;

/**
 * Conservative verification engine used when no execution-backed verifier is installed.
 *
 * <p>This implementation validates only that a proposal is present. It deliberately
 * does not claim syntax, lint, compilation, or test success without invoking a real
 * verifier. A toolchain-backed provider can replace this implementation.</p>
 */
@ApplicationScoped
public class DefaultVerificationEngine implements VerificationEngine {

    @Override
    public Uni<VerificationResult> verify(
            WorkspaceFingerprint fingerprint,
            EditProposal proposal,
            VerificationLevel level,
            Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (proposal == null || proposal.edits().isEmpty()) {
                return VerificationResult.failed(
                        level,
                        0,
                        List.of(TestFailureSummary.of(
                                "PatchValidation",
                                "EmptyProposal",
                                "No file edits found in proposal")),
                        "Verification cannot run without a non-empty edit proposal",
                        0L);
            }

            return VerificationResult.failed(
                    level,
                    0,
                    List.of(TestFailureSummary.of(
                            "VerificationProvider",
                            "NotConfigured",
                            "No execution-backed verifier is configured for " + level.name())),
                    "Verification was not run. Install a toolchain-backed verifier; "
                            + "heuristic source inspection is not accepted as verification evidence.",
                    0L);
        });
    }
}
