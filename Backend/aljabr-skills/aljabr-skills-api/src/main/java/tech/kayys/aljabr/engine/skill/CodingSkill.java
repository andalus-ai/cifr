package tech.kayys.aljabr.engine.skill;

import io.smallrye.mutiny.Uni;

public interface CodingSkill {

    SkillId id();

    SkillMetadata metadata();

    default SkillContract contract() {
        return new SkillContract(
            descriptor(),
            java.util.List.of(),
            java.util.List.of()
        );
    }

    default SkillDescriptor descriptor() {
        SkillMetadata meta = metadata();
        return SkillDescriptor.builder()
            .id(id() != null ? id().value() : "unknown")
            .name(meta != null ? meta.name() : "Unnamed")
            .description(meta != null ? meta.description() : "")
            .category(meta != null ? meta.category() : SkillCategory.DISCOVERY)
            .reasoningMode(meta != null ? meta.reasoningMode() : ReasoningMode.NONE)
            .mutatesWorkspace(meta != null && meta.mutatesWorkspace())
            .requiredCapabilities(meta != null ? meta.requiredCapabilities() : java.util.Set.of())
            .inputSchema(inputSchema() != null ? inputSchema().properties() : java.util.Map.of())
            .outputSchema(outputSchema() != null ? outputSchema().properties() : java.util.Map.of())
            .build();
    }

    default SkillInputSchema inputSchema() {
        return SkillInputSchema.empty();
    }

    default SkillOutputSchema outputSchema() {
        return SkillOutputSchema.empty();
    }

    Uni<SkillResult> execute(SkillContext context);
}
