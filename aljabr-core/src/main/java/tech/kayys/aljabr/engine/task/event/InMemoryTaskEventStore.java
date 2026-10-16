package tech.kayys.aljabr.engine.task.event;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.task.TaskId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class InMemoryTaskEventStore implements TaskEventStore {
    private final Map<String, List<TaskEvent>> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> append(TaskEvent event) {
        store.computeIfAbsent(event.taskId().value(), k -> new CopyOnWriteArrayList<>()).add(event);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Multi<TaskEvent> stream(TaskId taskId) {
        return Multi.createFrom().iterable(List.copyOf(store.getOrDefault(taskId.value(), List.of())));
    }

    @Override
    public Multi<TaskEvent> since(TaskId taskId, long afterRevision) {
        return stream(taskId).skip().first(afterRevision);
    }
}
