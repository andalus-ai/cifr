package tech.kayys.cifr.engine.verification;

/**
 * Declared guarantees of a verification execution backend.
 *
 * <p>These flags describe controls the backend claims to enforce, not controls
 * that Cifr itself can infer from a successful process exit. A backend must
 * report false for any control it cannot guarantee.</p>
 */
public record VerificationIsolationProfile(
        boolean filesystemNamespace,
        boolean workspaceWriteBoundary,
        boolean networkDisabled,
        boolean memoryLimitEnforced,
        boolean cpuLimitEnforced,
        boolean processLimitEnforced,
        boolean processTreeTermination,
        boolean environmentAllowlist,
        boolean outputLimitEnforced,
        boolean timeoutEnforced) {

    /** Conservative production baseline required before running repository code. */
    public boolean satisfiesStrictVerificationRequirements() {
        return filesystemNamespace
                && workspaceWriteBoundary
                && networkDisabled
                && memoryLimitEnforced
                && cpuLimitEnforced
                && processLimitEnforced
                && processTreeTermination
                && environmentAllowlist
                && outputLimitEnforced
                && timeoutEnforced;
    }

    public static VerificationIsolationProfile untrusted() {
        return new VerificationIsolationProfile(
                false, false, false, false, false,
                false, false, false, false, false);
    }
}
