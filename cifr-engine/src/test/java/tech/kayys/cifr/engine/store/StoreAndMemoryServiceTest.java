package tech.kayys.cifr.engine.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StoreAndMemoryServiceTest {

    private DefaultMemoryService memoryService;

    @BeforeEach
    public void setup() {
        memoryService = new DefaultMemoryService();
    }

    @Test
    public void testRememberAndQueryByScope() {
        Provenance prov = Provenance.of("test-runner", "1.0", "user-input");

        Memory m1 = Memory.create("mem-1", MemoryScope.PROJECT, MemoryType.CONSTRAINT, "Always use immutable DTOs", 0.9, prov);
        Memory m2 = Memory.create("mem-2", MemoryScope.TASK, MemoryType.FACT, "File TokenValidator.java has 150 lines", 0.5, prov);
        Memory m3 = Memory.create("mem-3", MemoryScope.PROJECT, MemoryType.PREFERENCE, "Prefer constructor injection", 0.8, prov);

        memoryService.remember(m1).await().indefinitely();
        memoryService.remember(m2).await().indefinitely();
        memoryService.remember(m3).await().indefinitely();

        List<Memory> projectMemories = memoryService.query(MemoryScope.PROJECT, null).await().indefinitely();
        assertEquals(2, projectMemories.size());

        List<Memory> constraints = memoryService.query(MemoryScope.PROJECT, MemoryType.CONSTRAINT).await().indefinitely();
        assertEquals(1, constraints.size());
        assertEquals("Always use immutable DTOs", constraints.get(0).content());
    }

    @Test
    public void testRevocationAndTombstoning() {
        Provenance prov = Provenance.of("test-runner", "1.0", "observation");

        Memory m1 = Memory.create("mem-revoke-1", MemoryScope.TASK, MemoryType.FACT, "Observed temporary failure", 0.3, prov);
        memoryService.remember(m1).await().indefinitely();

        List<Memory> before = memoryService.query(MemoryScope.TASK, null).await().indefinitely();
        assertEquals(1, before.size());

        // Forget individual memory
        memoryService.forget("mem-revoke-1").await().indefinitely();

        List<Memory> after = memoryService.query(MemoryScope.TASK, null).await().indefinitely();
        assertEquals(0, after.size(), "Forgotten memory must be tombstoned/inactive");
    }

    @Test
    public void testForgetByScope() {
        Provenance prov = Provenance.of("test-runner", "1.0", "observation");

        Memory m1 = Memory.create("m-task-1", MemoryScope.TASK, MemoryType.FACT, "Task fact 1", 0.5, prov);
        Memory m2 = Memory.create("m-task-2", MemoryScope.TASK, MemoryType.FACT, "Task fact 2", 0.5, prov);
        Memory m3 = Memory.create("m-proj-1", MemoryScope.PROJECT, MemoryType.FACT, "Project fact 1", 0.8, prov);

        memoryService.remember(m1).await().indefinitely();
        memoryService.remember(m2).await().indefinitely();
        memoryService.remember(m3).await().indefinitely();

        memoryService.forgetByScope(MemoryScope.TASK).await().indefinitely();

        List<Memory> remainingTasks = memoryService.query(MemoryScope.TASK, null).await().indefinitely();
        assertEquals(0, remainingTasks.size());

        List<Memory> remainingProjects = memoryService.query(MemoryScope.PROJECT, null).await().indefinitely();
        assertEquals(1, remainingProjects.size());
    }
}
