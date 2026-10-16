package tech.kayys.aljabr.engine.capability;

/**
 * Renders tool descriptors formatted according to the requested disclosure level.
 */
public interface ProgressiveToolDescriptor {

    /**
     * Renders the capability descriptor string for the specified progressive disclosure level.
     */
    String renderForLevel(Capability capability, CapabilityLevel level);
}
