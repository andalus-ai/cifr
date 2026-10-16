package tech.kayys.aljabr.engine.verification;

/**
 * Normalized fingerprint of a test failure for baseline comparison.
 */
public record TestFailureFingerprint(
    String testId,
    String failureCategory,
    String normalizedMessage,
    String assertionLocation
) {
    public static TestFailureFingerprint of(String testId, String category, String message, String location) {
        return new TestFailureFingerprint(testId, category, message, location);
    }
}
