package tech.kayys.aljabr.engine.exec.intelligence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record FailureFingerprint(
    String hash,
    FailureType failureType,
    String primaryLocation
) {
    public static FailureFingerprint of(FailureAnalysis analysis) {
        String location = analysis.primaryDiagnostics().isEmpty()
            ? "unknown"
            : analysis.primaryDiagnostics().get(0).file() + ":" + analysis.primaryDiagnostics().get(0).line();

        String raw = analysis.failureType().name() + "|" + location + "|" + analysis.summary();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            String hash = HexFormat.of().formatHex(digest).substring(0, 16);
            return new FailureFingerprint(hash, analysis.failureType(), location);
        } catch (NoSuchAlgorithmException e) {
            return new FailureFingerprint(String.valueOf(raw.hashCode()), analysis.failureType(), location);
        }
    }
}
