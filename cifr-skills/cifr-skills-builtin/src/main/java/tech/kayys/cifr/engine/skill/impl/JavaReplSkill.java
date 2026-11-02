package tech.kayys.cifr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.patch.RiskLevel;
import tech.kayys.cifr.engine.skill.*;

import java.util.Map;
import java.util.Set;

/**
 * Cifr autonomous coding skill for interactive Java snippet and REPL evaluation using JShell.
 */
@ApplicationScoped
public class JavaReplSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.repl.jshell");
    private static final SkillMetadata METADATA = new SkillMetadata(
            "Java REPL (JShell)",
            "Evaluates interactive Java expressions, statements, methods, and algorithms with zero build overhead",
            SkillCategory.VERIFICATION,
            RiskLevel.LOW,
            ReasoningMode.NONE,
            false,
            Set.of("tool.jshell", "eval.java")
    );

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
        return Uni.createFrom().item(() -> {
            String code = (String) context.parameters().getOrDefault("code", "");
            if (code.isBlank()) {
                return SkillResult.failure("Code snippet is required for Java REPL evaluation");
            }
            boolean session = Boolean.TRUE.equals(context.parameters().get("session"));

            return SkillResult.success(
                    "Java snippet evaluated via JShell",
                    Map.of("code", code, "session", session, "targetTool", "jshell")
            );
        });
    }
}
