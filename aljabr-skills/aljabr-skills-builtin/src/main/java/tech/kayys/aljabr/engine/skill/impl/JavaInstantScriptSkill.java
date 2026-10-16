package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aljabr autonomous coding skill for executing single-file Java applications and instant scripts with automated Maven dependency fetching via JBang.
 */
@ApplicationScoped
public class JavaInstantScriptSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.script.jbang");
    private static final SkillMetadata METADATA = new SkillMetadata(
            "Java Instant Script (JBang)",
            "Executes standalone Java applications, scripts, and prototypes with on-the-fly Maven dependencies and automatic runtime download",
            SkillCategory.VERIFICATION,
            RiskLevel.MEDIUM,
            ReasoningMode.NONE,
            false,
            Set.of("tool.jbang", "script.java", "net.deps")
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
            String scriptPath = (String) context.parameters().getOrDefault("scriptPath", "");
            if (code.isBlank() && scriptPath.isBlank()) {
                return SkillResult.failure("Either 'code' or 'scriptPath' must be provided for JBang execution");
            }

            Object deps = context.parameters().getOrDefault("dependencies", List.of());

            return SkillResult.success(
                    "Java instant script dispatched to JBang",
                    Map.of(
                            "code", code,
                            "scriptPath", scriptPath,
                            "dependencies", deps,
                            "targetTool", "jbang"
                    )
            );
        });
    }
}
