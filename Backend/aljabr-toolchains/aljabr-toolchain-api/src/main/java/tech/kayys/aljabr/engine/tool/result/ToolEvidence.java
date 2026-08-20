package tech.kayys.aljabr.engine.tool.result;
import java.time.Instant;
public record ToolEvidence(
    String id,
    String type,
    String contentHash,
    String workspaceFingerprint,
    Instant observedAt
) {
    public static ToolEvidence of(String type, String contentHash, String fingerprint) {
        return new ToolEvidence("ev-" + java.util.UUID.randomUUID(), type, contentHash, fingerprint, Instant.now());
    }
}
