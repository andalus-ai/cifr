package tech.kayys.cifr.engine.skill.runtime;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.skill.SkillSession;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemorySkillSessionStore implements SkillSessionStore {
    private final Map<String, SkillSession> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> save(SkillSession session) {
        if (session != null && session.taskId() != null) {
            store.put(session.taskId(), session);
        }
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Optional<SkillSession>> load(String taskId) {
        if (taskId == null) {
            return Uni.createFrom().item(Optional.empty());
        }
        return Uni.createFrom().item(Optional.ofNullable(store.get(taskId)));
    }
}
