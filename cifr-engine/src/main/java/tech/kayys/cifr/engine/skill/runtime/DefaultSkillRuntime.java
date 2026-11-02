package tech.kayys.cifr.engine.skill.runtime;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.skill.SkillDecision;
import tech.kayys.cifr.engine.skill.SkillDefinition;
import tech.kayys.cifr.engine.skill.SkillRuntime;
import tech.kayys.cifr.engine.skill.SkillSession;

import java.util.Map;

@ApplicationScoped
public class DefaultSkillRuntime implements SkillRuntime {

    @Inject
    SkillSessionStore sessionStore;

    @Inject
    SkillProjectionEngine projectionEngine;

    @Override
    public Uni<SkillSession> start(SkillDefinition skill, String taskId) {
        String entryPhase = skill != null && skill.phases() != null && skill.phases().entryPhase() != null
            ? skill.phases().entryPhase()
            : "UNDERSTANDING";
        SkillSession session = SkillSession.start(skill != null ? skill.id() : null, taskId, entryPhase);
        return sessionStore.save(session).replaceWith(session);
    }

    @Override
    public Uni<SkillDecision> decide(SkillSession session) {
        String nextCap = "code.symbol";
        return Uni.createFrom().item(new SkillDecision.RequestCapability(nextCap, Map.of()));
    }

    @Override
    public Uni<SkillSession> advance(SkillSession session, SkillDecision decision) {
        if (session == null) {
            return Uni.createFrom().nullItem();
        }
        SkillSession updated = switch (decision) {
            case SkillDecision.Finish f -> session.withPhase("COMPLETED");
            case SkillDecision.Blocked b -> session.withPhase("BLOCKED");
            default -> session;
        };
        return sessionStore.save(updated).replaceWith(updated);
    }
}
