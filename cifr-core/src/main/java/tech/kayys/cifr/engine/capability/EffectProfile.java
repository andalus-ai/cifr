package tech.kayys.cifr.engine.capability;

/**
 * Declares the runtime side-effect profile of a capability execution.
 */
public record EffectProfile(
    boolean readsState,
    boolean writesState,
    boolean touchesExternalNetwork,
    boolean destructive,
    CapabilityRisk riskLevel
) {
    public static EffectProfile readOnly() {
        return new EffectProfile(true, false, false, false, CapabilityRisk.SAFE);
    }

    public static EffectProfile localMutation() {
        return new EffectProfile(true, true, false, false, CapabilityRisk.MEDIUM);
    }

    public static EffectProfile externalNetwork() {
        return new EffectProfile(true, true, true, false, CapabilityRisk.HIGH);
    }

    public static EffectProfile highRiskDestructive() {
        return new EffectProfile(true, true, true, true, CapabilityRisk.CRITICAL);
    }
}
