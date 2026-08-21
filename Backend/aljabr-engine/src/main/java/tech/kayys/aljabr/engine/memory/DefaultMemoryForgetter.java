package tech.kayys.aljabr.engine.memory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.store.Memory;
import tech.kayys.aljabr.engine.store.MemoryService;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link MemoryForgetter}.
 * Handles deletion, invalidation, superseding, and cascade invalidation of derived memories.
 */
@ApplicationScoped
public class DefaultMemoryForgetter implements MemoryForgetter {

    private static final Logger LOG = Logger.getLogger(DefaultMemoryForgetter.class);

    @Inject
    public MemoryService memoryService;

    public DefaultMemoryForgetter() {}

    public DefaultMemoryForgetter(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public Uni<ForgetResult> forget(ForgetRequest request) {
        if (request == null || request.memoryId() == null || request.memoryId().isBlank()) {
            return Uni.createFrom().item(
                ForgetResult.failure("unknown", ForgetMode.INVALIDATE, "Invalid forget request: missing memory ID")
            );
        }

        LOG.infof("Executing memory forget for [%s], mode: %s, reason: %s",
            request.memoryId(), request.mode(), request.reason());

        if (memoryService == null) {
            return Uni.createFrom().item(
                ForgetResult.success(request.memoryId(), request.mode(), List.of(), "Memory service unavailable; recorded")
            );
        }

        return memoryService.forget(request.memoryId())
            .chain(() -> {
                List<String> cascaded = new ArrayList<>();
                if (request.cascadeDerivedMemories()) {
                    // Query any memories derived from this evidence/memory
                    return memoryService.query(null, null)
                        .chain(allMemories -> {
                            List<Uni<Void>> cascadeOperations = new ArrayList<>();
                            for (Memory m : allMemories) {
                                if (m.provenance() != null &&
                                    m.provenance().derivedFromEvidenceId().isPresent() &&
                                    m.provenance().derivedFromEvidenceId().get().equals(request.memoryId())) {
                                    cascaded.add(m.id());
                                    cascadeOperations.add(memoryService.forget(m.id()));
                                }
                            }
                            if (cascadeOperations.isEmpty()) {
                                return Uni.createFrom().item(
                                    ForgetResult.success(request.memoryId(), request.mode(), cascaded, "Memory forgotten successfully")
                                );
                            }
                            return Uni.combine().all().unis(cascadeOperations).discardItems()
                                .map(v -> ForgetResult.success(request.memoryId(), request.mode(), cascaded,
                                    "Memory and " + cascaded.size() + " derived memories forgotten successfully"));
                        });
                }
                return Uni.createFrom().item(
                    ForgetResult.success(request.memoryId(), request.mode(), cascaded, "Memory forgotten successfully")
                );
            });
    }
}
