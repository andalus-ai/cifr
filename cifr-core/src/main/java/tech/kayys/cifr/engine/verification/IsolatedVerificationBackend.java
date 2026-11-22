package tech.kayys.cifr.engine.verification;

import io.smallrye.mutiny.Uni;

/**
 * Executes repository-controlled verification commands inside an isolated
 * environment and returns execution-bound evidence.
 *
 * <p>Implementations must accurately declare their enforced controls through
 * {@link #isolationProfile()}. They must never claim PASSED unless the command
 * completed successfully inside the requested workspace revision.</p>
 */
public interface IsolatedVerificationBackend {

    String backendId();

    VerificationIsolationProfile isolationProfile();

    Uni<VerificationExecutionResult> execute(VerificationCheckRequest request);
}
