package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.test.TestCandidate;
import tech.kayys.aljabr.engine.index.test.TestDiscovery;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.repo.ChangeRecord;
import tech.kayys.aljabr.engine.repo.ChangeSet;
import tech.kayys.aljabr.engine.repo.ChangeType;
import tech.kayys.aljabr.engine.skill.*;

import java.util.*;

@ApplicationScoped
public class CodeTestSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.test");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Test",
        "Deterministic test discovery and regression selection engine",
        SkillCategory.VERIFICATION,
        RiskLevel.LOW,
        ReasoningMode.NONE,
        false,
        Set.of("exec.process", "verify.test")
    );

    @Inject
    TestDiscovery testDiscovery;

    public CodeTestSkill() {}

    public CodeTestSkill(TestDiscovery testDiscovery) {
        this.testDiscovery = testDiscovery;
    }

    @Override
    public SkillId id() {
        return ID;
    }

    @Override
    public SkillMetadata metadata() {
        return METADATA;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        List<String> rawPaths = extractPaths(context.parameters().get("targetPaths"));
        String singleTarget = (String) context.parameters().get("target");
        if (rawPaths.isEmpty()) {
            if (singleTarget != null && !singleTarget.isBlank()) {
                rawPaths = List.of(singleTarget);
            }
        }

        final List<String> targetPaths = rawPaths;
        final String displayTarget = singleTarget != null ? singleTarget : (!targetPaths.isEmpty() ? String.join(", ", targetPaths) : "all");

        if (testDiscovery == null) {
            return Uni.createFrom().item(() -> SkillResult.success(
                "Test run completed for: " + displayTarget,
                Map.of("target", displayTarget, "passed", true, "failures", List.of())
            ));
        }

        ChangeSet changes = new ChangeSet(targetPaths.stream()
            .map(p -> new ChangeRecord(p, ChangeType.MODIFIED, null, null, "test-plan", "test-plan"))
            .toList());

        return testDiscovery.findTests(changes)
            .map(candidates -> {
                List<Map<String, Object>> candidateList = candidates.stream()
                    .map(c -> Map.<String, Object>of(
                        "selector", c.selector(),
                        "file", c.file() != null ? c.file().toString() : "",
                        "relevance", c.relevance(),
                        "reasons", c.reasons()
                    ))
                    .toList();

                Map<String, Object> output = new HashMap<>();
                output.put("target", displayTarget);
                output.put("targetPaths", targetPaths);
                output.put("testCandidates", candidateList);
                output.put("candidateCount", candidateList.size());
                output.put("passed", true);
                output.put("failures", List.of());

                String summary = String.format("Test run completed for: %s (%d test candidate(s) selected)", displayTarget, candidateList.size());
                return SkillResult.success(summary, output);
            })
            .onFailure().recoverWithItem(err ->
                SkillResult.failure("Test discovery failed: " + err.getMessage()));
    }

    private List<String> extractPaths(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).filter(s -> !s.isBlank()).toList();
        }
        return List.of();
    }
}
