package tech.kayys.cifr.engine.patch;

/**
 * Deterministic fingerprint of a proposed patch for repetition/loop detection.
 */
public record PatchFingerprint(
    String targetHash,
    String mutationHash,
    String baseRevision
) {
    public static PatchFingerprint of(String targetHash, String mutationHash, String baseRevision) {
        return new PatchFingerprint(targetHash, mutationHash, baseRevision);
    }
}
