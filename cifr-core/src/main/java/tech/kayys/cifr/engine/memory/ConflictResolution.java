package tech.kayys.cifr.engine.memory;

import tech.kayys.cifr.engine.store.Memory;
import java.util.Optional;

/**
 * Result of resolving a conflict between an existing memory and a new candidate.
 */
public record ConflictResolution(
    ResolutionStrategy strategy,
    String explanation,
    Optional<Memory> resolvedMemory
) {
    public static ConflictResolution keepExisting(String reason, Memory existing) {
        return new ConflictResolution(ResolutionStrategy.KEEP_EXISTING, reason, Optional.of(existing));
    }

    public static ConflictResolution supersedeExisting(String reason, Memory incoming) {
        return new ConflictResolution(ResolutionStrategy.SUPERSEDE_EXISTING, reason, Optional.of(incoming));
    }

    public static ConflictResolution dispute(String reason) {
        return new ConflictResolution(ResolutionStrategy.KEEP_BOTH, reason, Optional.empty());
    }

    public static ConflictResolution requireVerification(String reason) {
        return new ConflictResolution(ResolutionStrategy.REQUIRE_VERIFICATION, reason, Optional.empty());
    }
}
