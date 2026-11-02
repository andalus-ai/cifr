package tech.kayys.cifr.engine.capability;

/**
 * Declares an atomic runtime capability that the agent can be authorized to propose.
 */
public record Capability(
    String id,
    String name,
    CapabilityCategory category,
    CapabilityRisk risk,
    CapabilityContract contract
) {
    public static Capability of(String id, String name, CapabilityCategory category, CapabilityRisk risk) {
        return new Capability(id, name, category, risk, CapabilityContract.simple(risk, Idempotency.SAFE));
    }
}
