package tech.kayys.aljabr.engine.store;

/**
 * Composite key for reusable computational artifacts.
 */
public record CacheKey(
    String operation,
    String inputFingerprint,
    String environmentFingerprint,
    String implementationVersion
) {
    public static CacheKey of(String operation, String inputFingerprint) {
        return new CacheKey(operation, inputFingerprint, "default", "1.0");
    }
}
