package tech.kayys.cifr.engine.verification;

import io.smallrye.mutiny.Uni;

/**
 * Executes one trusted verification check and returns execution-bound evidence.
 * Implementations must never report PASSED without a successful process result.
 */
public interface VerificationProvider {

    Uni<VerificationExecutionResult> execute(VerificationCheckRequest request);
}
