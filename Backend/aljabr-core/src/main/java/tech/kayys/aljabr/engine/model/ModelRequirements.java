package tech.kayys.aljabr.engine.model;

/**
 * Requirements declared by an agent step or task that the executing model must satisfy.
 */
public record ModelRequirements(
    long minimumContextTokens,
    boolean requiresToolCalling,
    boolean requiresStructuredOutput,
    boolean requiresVision,
    boolean requiresReasoning,
    PrivacyRequirement privacyRequirement,
    ModelRoutingStrategy strategy
) {
    public static ModelRequirements standardLocal() {
        return new ModelRequirements(
            4096L,
            true,
            true,
            false,
            false,
            PrivacyRequirement.CLOUD_ALLOWED,
            ModelRoutingStrategy.LOCAL_FIRST
        );
    }

    public static ModelRequirements privateLocalOnly() {
        return new ModelRequirements(
            4096L,
            true,
            true,
            false,
            false,
            PrivacyRequirement.LOCAL_ONLY,
            ModelRoutingStrategy.PRIVACY_FIRST
        );
    }

    public static ModelRequirements deepReasoning() {
        return new ModelRequirements(
            16384L,
            true,
            true,
            false,
            true,
            PrivacyRequirement.CLOUD_ALLOWED,
            ModelRoutingStrategy.QUALITY_FIRST
        );
    }
}
