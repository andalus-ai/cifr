package tech.kayys.aljabr.engine.skill.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.skill.SkillSession;

import java.util.Optional;

public interface SkillSessionStore {
    Uni<Void> save(SkillSession session);
    Uni<Optional<SkillSession>> load(String taskId);
}
