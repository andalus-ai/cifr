package tech.kayys.cifr.engine.skill.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.skill.SkillSession;

import java.util.Optional;

public interface SkillSessionStore {
    Uni<Void> save(SkillSession session);
    Uni<Optional<SkillSession>> load(String taskId);
}
