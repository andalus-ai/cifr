package tech.kayys.aljabr.engine.memory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.store.Memory;
import tech.kayys.aljabr.engine.store.MemoryLifecycle;
import tech.kayys.aljabr.engine.store.MemoryScope;
import tech.kayys.aljabr.engine.store.MemoryService;
import tech.kayys.aljabr.engine.store.MemoryType;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link MemoryConsolidator}.
 * Evaluates active memories in scope and consolidates high-importance knowledge into durable scopes.
 */
@ApplicationScoped
public class DefaultMemoryConsolidator implements MemoryConsolidator {

    private static final Logger LOG = Logger.getLogger(DefaultMemoryConsolidator.class);

    @Inject
    public MemoryService memoryService;

    public DefaultMemoryConsolidator() {}

    public DefaultMemoryConsolidator(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public Uni<ConsolidationReport> consolidate(String scopeId, MemoryScope scope) {
        LOG.infof("Starting memory consolidation for scope [%s], level: %s", scopeId, scope);

        if (memoryService == null) {
            return Uni.createFrom().item(new ConsolidationReport(scopeId != null ? scopeId : "global", 0, 0, 0, 0, 0, List.of()));
        }

        return memoryService.query(scope, null)
            .chain(memories -> {
                int evaluated = memories.size();
                int episodic = 0;
                int semantic = 0;
                int procedural = 0;
                int expired = 0;
                List<String> consolidated = new ArrayList<>();
                List<Uni<Void>> mutations = new ArrayList<>();

                for (Memory mem : memories) {
                    if (mem.importance() <= 0.25 || mem.lifecycle() == MemoryLifecycle.REVOKED) {
                        expired++;
                        mutations.add(memoryService.forget(mem.id()));
                        continue;
                    }

                    // Consolidate high-importance task/session memories into Project or Global scopes
                    if (mem.importance() >= 0.85 && mem.type() == MemoryType.PROCEDURE) {
                        procedural++;
                        consolidated.add(mem.id());
                        Memory promoted = new Memory(
                            mem.id(), MemoryScope.GLOBAL, MemoryType.PROCEDURE,
                            mem.content(), mem.importance(), mem.provenance(),
                            mem.lifecycle(), mem.createdAt(), java.time.Instant.now()
                        );
                        mutations.add(memoryService.remember(promoted));
                    } else if (mem.importance() >= 0.75 && (mem.type() == MemoryType.FACT || mem.type() == MemoryType.CONSTRAINT)) {
                        semantic++;
                        consolidated.add(mem.id());
                        Memory promoted = new Memory(
                            mem.id(), MemoryScope.PROJECT, mem.type(),
                            mem.content(), mem.importance(), mem.provenance(),
                            mem.lifecycle(), mem.createdAt(), java.time.Instant.now()
                        );
                        mutations.add(memoryService.remember(promoted));
                    } else if (mem.importance() >= 0.50 && mem.scope() == MemoryScope.INVOCATION) {
                        episodic++;
                        consolidated.add(mem.id());
                        Memory promoted = new Memory(
                            mem.id(), MemoryScope.TASK, mem.type(),
                            mem.content(), mem.importance(), mem.provenance(),
                            mem.lifecycle(), mem.createdAt(), java.time.Instant.now()
                        );
                        mutations.add(memoryService.remember(promoted));
                    }
                }

                ConsolidationReport report = new ConsolidationReport(
                    scopeId != null ? scopeId : "global",
                    evaluated,
                    episodic,
                    semantic,
                    procedural,
                    expired,
                    consolidated
                );

                if (mutations.isEmpty()) {
                    return Uni.createFrom().item(report);
                }

                return Uni.combine().all().unis(mutations).discardItems().map(v -> report);
            });
    }
}
