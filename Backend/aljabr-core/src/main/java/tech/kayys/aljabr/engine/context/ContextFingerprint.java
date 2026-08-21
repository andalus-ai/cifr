package tech.kayys.aljabr.engine.context;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic hash fingerprint of an assembled ContextPacket for inference reuse.
 */
public record ContextFingerprint(String hash) {
    public static ContextFingerprint compute(String contextString) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(contextString != null ? contextString.getBytes() : new byte[0]);
            return new ContextFingerprint(HexFormat.of().formatHex(bytes));
        } catch (NoSuchAlgorithmException e) {
            return new ContextFingerprint(Integer.toHexString(contextString != null ? contextString.hashCode() : 0));
        }
    }
}
