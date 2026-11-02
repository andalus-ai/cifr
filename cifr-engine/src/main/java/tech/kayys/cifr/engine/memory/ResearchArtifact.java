package tech.kayys.cifr.engine.memory;

import java.time.Instant;
import java.util.List;

public record ResearchArtifact(
    String id,
    String query,
    String source,
    String contentHash,
    String summary,
    Instant createdAt,
    // Provenance fields
    String tool,
    String executionId,
    String stepId,
    List<String> parentArtifacts,
    double confidence
) {}
