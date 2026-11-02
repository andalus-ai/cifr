package tech.kayys.cifr.engine.memory;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.store.Memory;
import tech.kayys.cifr.engine.store.MemoryScope;
import tech.kayys.cifr.engine.store.MemoryService;
import tech.kayys.cifr.engine.store.MemoryType;
import tech.kayys.cifr.engine.store.Provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryRetrieverAndRankerTest {

    private MemoryService memoryService;
    private MemoryRanker memoryRanker;
    private DefaultMemoryRetriever retriever;
    private DefaultMemoryForgetPolicy forgetPolicy;

    @BeforeEach
    void setUp() {
        memoryService = mock(MemoryService.class);
        memoryRanker = new DefaultMemoryRanker();
        retriever = new DefaultMemoryRetriever(memoryService, memoryRanker);
        forgetPolicy = new DefaultMemoryForgetPolicy();
    }

    @Test
    void testRetrieveAndRankMatchingMemories() {
        Memory mem1 = Memory.create(
            "mem-1",
            MemoryScope.PROJECT,
            MemoryType.FACT,
            "Authentication service lives in auth-core module",
            0.8,
            Provenance.of("scan", "1.0", "repo")
        );
        Memory mem2 = Memory.create(
            "mem-2",
            MemoryScope.TASK,
            MemoryType.FACT,
            "Temporary auth test mock token",
            0.5,
            Provenance.of("test", "1.0", "local")
        );

        when(memoryService.query(any(), any()))
            .thenReturn(Uni.createFrom().item(List.of(mem1, mem2)));

        MemoryQuery query = MemoryQuery.of("auth-core", Set.of(MemoryScope.PROJECT), 5);
        List<MemoryMatch> matches = retriever.retrieve(query).await().indefinitely();

        assertNotNull(matches);
        assertFalse(matches.isEmpty());
        assertEquals("mem-1", matches.get(0).memory().id());
        assertTrue(matches.get(0).score() > matches.get(1).score());
    }

    @Test
    void testForgetPolicyEvaluation() {
        Memory mem = Memory.create("mem-x", MemoryScope.TASK, MemoryType.PREFERENCE, "User prefers dark mode", 0.5, Provenance.of("user", "1.0", "chat"));

        assertEquals(ForgetDecision.FORGET, forgetPolicy.evaluate(mem, "User requested forgetting"));
        assertEquals(ForgetDecision.REVOKE, forgetPolicy.evaluate(mem, "Contradicted by newer test"));
        
        Memory globalMem = Memory.create("mem-g", MemoryScope.GLOBAL, MemoryType.FACT, "Java 25 is active", 0.9, Provenance.of("sys", "1.0", "env"));
        assertEquals(ForgetDecision.ARCHIVE, forgetPolicy.evaluate(globalMem, "Maintenance cycle"));
    }
}
