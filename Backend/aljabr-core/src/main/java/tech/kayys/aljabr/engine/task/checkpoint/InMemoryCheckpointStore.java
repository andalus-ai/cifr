package tech.kayys.aljabr.engine.task.checkpoint;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.task.TaskId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class InMemoryCheckpointStore implements CheckpointStore {
    private final Map<String, List<Checkpoint>> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> save(Checkpoint cp) {
        store.computeIfAbsent(cp.taskId().value(), k -> new CopyOnWriteArrayList<>()).add(cp);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Optional<Checkpoint>> latest(TaskId taskId) {
        List<Checkpoint> list = store.getOrDefault(taskId.value(), List.of());
        return Uni.createFrom().item(list.isEmpty() ? Optional.empty() : Optional.of(list.get(list.size() - 1)));
    }

    @Override
    public Uni<List<Checkpoint>> list(TaskId taskId) {
        return Uni.createFrom().item(List.copyOf(store.getOrDefault(taskId.value(), List.of())));
    }
}
