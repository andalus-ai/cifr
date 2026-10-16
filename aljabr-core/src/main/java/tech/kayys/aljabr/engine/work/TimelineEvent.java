package tech.kayys.aljabr.engine.work;

import java.time.Instant;

public record TimelineEvent(
    String id,
    String executionId,
    String phase,
    String eventType,
    String title,
    String details,
    Instant timestamp
) {
    public TimelineEvent {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
