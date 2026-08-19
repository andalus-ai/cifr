package tech.kayys.aljabr.engine.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.skill.reasoning.ReasoningDecision;
import tech.kayys.aljabr.engine.skill.reasoning.ReasoningGate;
import tech.kayys.aljabr.engine.skill.routing.SkillSelection;
import tech.kayys.aljabr.engine.skill.routing.SkillSelector;
import tech.kayys.aljabr.engine.skill.telemetry.SkillExecutionRecord;
import tech.kayys.aljabr.engine.skill.telemetry.SkillTelemetryCollector;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class SkillRouter {

    private static final Logger LOG = Logger.getLogger(SkillRouter.class);

    @Inject
    SkillRegistry skillRegistry;

    @Inject
    ReasoningGate reasoningGate;

    @Inject
    SkillSelector skillSelector;

    @Inject
    SkillTelemetryCollector telemetryCollector;

    public SkillRouter() {
    }

    public SkillRouter(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public SkillRouter(SkillRegistry skillRegistry, ReasoningGate reasoningGate, SkillSelector skillSelector, SkillTelemetryCollector telemetryCollector) {
        this.skillRegistry = skillRegistry;
        this.reasoningGate = reasoningGate;
        this.skillSelector = skillSelector;
        this.telemetryCollector = telemetryCollector;
    }

    public Optional<SkillSelection> select(String query, SkillContext context) {
        if (skillSelector != null) {
            return skillSelector.select(query, context);
        }
        return Optional.empty();
    }

    public Uni<SkillResult> dispatch(String skillId, SkillContext context) {
        if (skillId == null || skillId.isBlank()) {
            return Uni.createFrom().item(SkillResult.failure("Skill identifier cannot be empty"));
        }

        var skillOpt = skillRegistry.find(skillId);
        if (skillOpt.isEmpty()) {
            return Uni.createFrom().item(SkillResult.failure("Skill not found: " + skillId));
        }

        CodingSkill skill = skillOpt.get();
        SkillDescriptor descriptor = skill.descriptor();
        LOG.debugf("Routing skill '%s' (Category: %s, ReasoningMode: %s)",
            descriptor.id(), descriptor.category(), descriptor.reasoningMode());

        ReasoningDecision decision = reasoningGate != null ?
            reasoningGate.evaluate(skillId, context, descriptor) : ReasoningDecision.SKIP_MODEL;

        // Validate preconditions if any
        if (skill.contract() != null && skill.contract().preconditions() != null) {
            for (SkillPrecondition pre : skill.contract().preconditions()) {
                if (!pre.satisfied()) {
                    return Uni.createFrom().item(SkillResult.failure(
                        "Precondition violated for skill '" + skillId + "': " + pre.description()));
                }
            }
        }

        Instant startedAt = Instant.now();

        return skill.execute(context)
            .onFailure().recoverWithItem(err -> {
                LOG.errorf("Error executing skill %s: %s", skillId, err.getMessage());
                return SkillResult.failure("Skill execution failed: " + err.getMessage(),
                    Map.of("error", err.getMessage() != null ? err.getMessage() : "Unknown error"));
            })
            .invoke(result -> {
                Instant completedAt = Instant.now();
                long durationMs = Duration.between(startedAt, completedAt).toMillis();

                if (telemetryCollector != null) {
                    String executionId = context != null && context.executionId() != null ?
                        context.executionId() : "exec-" + System.currentTimeMillis();

                    telemetryCollector.record(new SkillExecutionRecord(
                        executionId,
                        skillId,
                        startedAt,
                        completedAt,
                        durationMs,
                        result.isSuccess(),
                        decision,
                        result.evidenceIds()
                    ));
                }
            });
    }
}
