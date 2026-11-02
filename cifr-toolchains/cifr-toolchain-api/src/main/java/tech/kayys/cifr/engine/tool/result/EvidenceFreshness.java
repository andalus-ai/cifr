package tech.kayys.cifr.engine.tool.result;

import java.time.Duration;
import java.time.Instant;
import tech.kayys.cifr.engine.tool.request.ToolRequest;

/**
 * Evidence freshness tied to workspace revision, enabling smart cache invalidation.
 */
public record EvidenceFreshness(
    String workspaceRevision,
    Instant createdAt,
    Duration ttl
) {
    /** Check if this evidence is still fresh relative to the current workspace revision. */
    public boolean isFresh(String currentRevision) {
        if (!workspaceRevision.equals(currentRevision)) {
            return false;
        }
        if (ttl != null && ttl != Duration.ZERO) {
            return Instant.now().isBefore(createdAt.plus(ttl));
        }
        return true;
    }

    public static EvidenceFreshness forRevision(String revision) {
        return new EvidenceFreshness(revision, Instant.now(), null);
    }

    public static EvidenceFreshness withTtl(String revision, Duration ttl) {
        return new EvidenceFreshness(revision, Instant.now(), ttl);
    }
}
