package tech.kayys.aljabr.engine.verification;

/**
 * Composite key for caching deterministic verification results per workspace revision and stage.
 */
public record VerificationCacheKey(
    String workspaceRevision,
    VerificationStage stage,
    String targetIdentifier,
    String toolVersion,
    String environmentFingerprint
) {
    public static VerificationCacheKey of(String revision, VerificationStage stage, String target) {
        return new VerificationCacheKey(revision, stage, target, "default", "default");
    }
}
