package tech.kayys.aljabr.engine.work;

import java.time.Instant;

public record WorkItem(
    String id,
    String workspaceId,
    WorkType type,
    String title,
    String description,
    WorkStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public WorkItem {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }
}
