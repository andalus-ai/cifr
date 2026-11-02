package tech.kayys.cifr.engine.store;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.task.TaskId;

import java.util.List;
import java.util.Optional;

/**
 * Store for managing immutable execution evidence.
 */
public interface EvidenceStore {

    Uni<Void> save(Evidence evidence);

    Uni<Optional<Evidence>> get(String id);

    Uni<List<Evidence>> findByTask(TaskId taskId);
}
