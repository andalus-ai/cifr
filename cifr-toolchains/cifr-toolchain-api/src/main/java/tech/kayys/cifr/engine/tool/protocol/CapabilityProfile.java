package tech.kayys.cifr.engine.tool.protocol;
import java.util.Set;
public record CapabilityProfile(
    Set<String> supportedPhases,
    Set<String> modelRequirements,
    ToolMetadata metadata
) {
    public CapabilityProfile { supportedPhases = supportedPhases != null ? Set.copyOf(supportedPhases) : Set.of(); modelRequirements = modelRequirements != null ? Set.copyOf(modelRequirements) : Set.of(); }
}
