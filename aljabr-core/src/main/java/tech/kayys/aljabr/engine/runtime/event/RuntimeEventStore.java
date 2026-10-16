package tech.kayys.aljabr.engine.runtime.event;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.task.TaskId;

import java.util.List;
import java.util.Optional;

/**
 * Append-only store for immutable runtime events.
 */
public interface RuntimeEventStore {

    Uni<EventId> append(RuntimeEvent event);

    Uni<List<RuntimeEvent>> read(TaskId taskId, long fromSequence);

    Uni<Optional<RuntimeEvent>> get(EventId eventId);
}
