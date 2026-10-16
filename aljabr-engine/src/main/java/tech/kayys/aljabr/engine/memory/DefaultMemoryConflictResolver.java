package tech.kayys.aljabr.engine.memory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.store.Memory;

/**
 * Default epistemic conflict resolver.
 * Evaluates memory importance, creation timestamps, and provenance certainty.
 */
@ApplicationScoped
public class DefaultMemoryConflictResolver implements MemoryConflictResolver {

    @Override
    public Uni<ConflictResolution> resolve(Memory existing, Memory incoming) {
        if (existing == null && incoming == null) {
            return Uni.createFrom().item(ConflictResolution.dispute("Both existing and incoming memories are null"));
        }
        if (existing == null) {
            return Uni.createFrom().item(ConflictResolution.supersedeExisting("No existing memory to conflict with", incoming));
        }
        if (incoming == null) {
            return Uni.createFrom().item(ConflictResolution.keepExisting("Incoming candidate is null", existing));
        }

        // 1. If incoming has higher importance by >= 0.3, it supersedes existing
        if (incoming.importance() - existing.importance() >= 0.3) {
            return Uni.createFrom().item(
                ConflictResolution.supersedeExisting(
                    "Incoming memory has significantly higher epistemic importance (" + incoming.importance() + " vs " + existing.importance() + ")",
                    incoming
                )
            );
        }

        // 2. If existing has higher importance by >= 0.3, keep existing
        if (existing.importance() - incoming.importance() >= 0.3) {
            return Uni.createFrom().item(
                ConflictResolution.keepExisting(
                    "Existing memory has significantly higher epistemic importance (" + existing.importance() + " vs " + incoming.importance() + ")",
                    existing
                )
            );
        }

        // 3. If incoming is strictly newer and equal or greater importance, supersede
        if (incoming.updatedAt().isAfter(existing.updatedAt()) && incoming.importance() >= existing.importance()) {
            return Uni.createFrom().item(
                ConflictResolution.supersedeExisting("Incoming memory is newer and has comparable importance", incoming)
            );
        }

        // 4. Otherwise dispute
        return Uni.createFrom().item(
            ConflictResolution.dispute("Memories conflict with comparable importance and conflicting claims")
        );
    }
}
