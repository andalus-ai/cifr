package tech.kayys.cifr.engine.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.store.DefaultMemoryService;
import tech.kayys.cifr.engine.store.Memory;
import tech.kayys.cifr.engine.store.MemoryLifecycle;
import tech.kayys.cifr.engine.store.MemoryScope;
import tech.kayys.cifr.engine.store.MemoryType;
import tech.kayys.cifr.engine.store.Provenance;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryEpistemicLifecycleTest {

    private DefaultMemoryService memoryService;
    private DefaultMemoryScopeResolver scopeResolver;
    private DefaultMemoryConflictResolver conflictResolver;
    private DefaultMemoryForgetter forgetter;
    private DefaultMemoryConsolidator consolidator;

    @BeforeEach
    void setUp() {
        memoryService = new DefaultMemoryService();
        scopeResolver = new DefaultMemoryScopeResolver();
        conflictResolver = new DefaultMemoryConflictResolver();

        forgetter = new DefaultMemoryForgetter();
        forgetter.memoryService = memoryService;

        consolidator = new DefaultMemoryConsolidator();
        consolidator.memoryService = memoryService;
    }

    @Test
    void testHierarchicalScopeVisibility() {
        Set<MemoryScope> invocationScopes = scopeResolver.resolveVisibleScopes(MemoryScope.INVOCATION);
        assertTrue(invocationScopes.contains(MemoryScope.INVOCATION));
        assertTrue(invocationScopes.contains(MemoryScope.TASK));
        assertTrue(invocationScopes.contains(MemoryScope.SESSION));
        assertTrue(invocationScopes.contains(MemoryScope.GLOBAL));

        Set<MemoryScope> userScopes = scopeResolver.resolveVisibleScopes(MemoryScope.USER);
        assertEquals(2, userScopes.size());
        assertTrue(userScopes.contains(MemoryScope.USER));
        assertTrue(userScopes.contains(MemoryScope.GLOBAL));
        assertTrue(scopeResolver.isAccessible(MemoryScope.GLOBAL, MemoryScope.TASK));
        assertFalse(scopeResolver.isAccessible(MemoryScope.INVOCATION, MemoryScope.PROJECT));
    }

    @Test
    void testConflictResolutionByImportanceAndRecency() {
        Memory existing = Memory.create("mem-1", MemoryScope.TASK, MemoryType.FACT, "Build with Java 21", 0.5, Provenance.of("test", "1.0", null));
        Memory incomingHigher = Memory.create("mem-2", MemoryScope.TASK, MemoryType.FACT, "Build with Java 25", 0.9, Provenance.of("test", "1.0", null));

        ConflictResolution resolution = conflictResolver.resolve(existing, incomingHigher).await().atMost(Duration.ofSeconds(2));
        assertEquals(ResolutionStrategy.SUPERSEDE_EXISTING, resolution.strategy());
        assertTrue(resolution.resolvedMemory().isPresent());
        assertEquals("Build with Java 25", resolution.resolvedMemory().get().content());
    }

    @Test
    void testCascadeForgetting() {
        Memory root = Memory.create("root-1", MemoryScope.TASK, MemoryType.FACT, "Compiler config", 0.7, Provenance.of("test", "1.0", null));
        Memory derived = new Memory(
            "derived-1", MemoryScope.TASK, MemoryType.FACT, "Derived build flag", 0.6,
            Provenance.derived("test", "root-1"), MemoryLifecycle.ACTIVE, Instant.now(), Instant.now()
        );

        memoryService.remember(root).await().indefinitely();
        memoryService.remember(derived).await().indefinitely();

        ForgetRequest request = ForgetRequest.delete("root-1", "Superseded by user directive");
        ForgetResult result = forgetter.forget(request).await().atMost(Duration.ofSeconds(2));

        assertTrue(result.success());
        assertTrue(result.cascadedMemoryIds().contains("derived-1"));

        List<Memory> activeMemories = memoryService.query(MemoryScope.TASK, null).await().indefinitely();
        assertTrue(activeMemories.isEmpty());
    }

    @Test
    void testMemoryConsolidationAcrossScopes() {
        Memory procedureMem = Memory.create("proc-1", MemoryScope.TASK, MemoryType.PROCEDURE, "Run mvn clean compile", 0.9, Provenance.of("agent", "1.0", null));
        Memory factMem = Memory.create("fact-1", MemoryScope.TASK, MemoryType.FACT, "Quarkus 3.32 is target runtime", 0.8, Provenance.of("agent", "1.0", null));
        Memory lowImportanceMem = Memory.create("low-1", MemoryScope.TASK, MemoryType.FACT, "Temporary build scratch", 0.1, Provenance.of("agent", "1.0", null));

        memoryService.remember(procedureMem).await().indefinitely();
        memoryService.remember(factMem).await().indefinitely();
        memoryService.remember(lowImportanceMem).await().indefinitely();

        ConsolidationReport report = consolidator.consolidate("task-100", MemoryScope.TASK).await().atMost(Duration.ofSeconds(2));

        assertEquals(1, report.proceduralPromotions());
        assertEquals(1, report.semanticPromotions());
        assertEquals(1, report.expiredOrInvalidated());
        assertTrue(report.consolidatedMemoryIds().contains("proc-1"));
        assertTrue(report.consolidatedMemoryIds().contains("fact-1"));
    }
}
