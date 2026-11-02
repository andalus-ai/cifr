package tech.kayys.cifr.engine.execution;

import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.capability.DefaultCapabilityAuthorizer;
import tech.kayys.cifr.engine.capability.DefaultCapabilityRegistry;
import tech.kayys.cifr.engine.capability.DefaultProgressiveToolResolver;
import tech.kayys.cifr.engine.memory.DefaultMemoryConsolidator;
import tech.kayys.cifr.engine.model.ModelRequirements;
import tech.kayys.cifr.engine.router.ResilientModelRouter;
import tech.kayys.cifr.engine.store.DefaultMemoryService;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExecutionStateMachineAndOrchestratorTest {

    private DefaultExecutionStateMachine stateMachine;
    private DefaultExecutionController controller;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stateMachine = new DefaultExecutionStateMachine();
        Instance<ExecutionStateHandler> mockHandlers = mock(Instance.class);
        when(mockHandlers.iterator()).thenReturn(Collections.emptyIterator());
        stateMachine.stateHandlers = mockHandlers;

        controller = new DefaultExecutionController();

        DefaultCapabilityRegistry capabilityRegistry = new DefaultCapabilityRegistry();
        DefaultProgressiveToolResolver toolResolver = new DefaultProgressiveToolResolver();
        toolResolver.capabilityRegistry = capabilityRegistry;

        DefaultCapabilityAuthorizer authorizer = new DefaultCapabilityAuthorizer();
        authorizer.capabilityRegistry = capabilityRegistry;

        DefaultMemoryService memoryService = new DefaultMemoryService();
        DefaultMemoryConsolidator consolidator = new DefaultMemoryConsolidator();
        consolidator.memoryService = memoryService;

        ResilientModelRouter router = new ResilientModelRouter();

        orchestrator = new AgentOrchestrator();
        orchestrator.stateMachine = stateMachine;
        orchestrator.modelRouter = router;
        orchestrator.toolResolver = toolResolver;
        orchestrator.capabilityAuthorizer = authorizer;
        orchestrator.memoryConsolidator = consolidator;
    }

    @Test
    void testBudgetTrackerExhaustionGuards() {
        DefaultExecutionBudgetTracker tracker = new DefaultExecutionBudgetTracker();
        ExecutionBudget budget = new ExecutionBudget(3, 10, 10, 2, Duration.ofMinutes(5), 1000L);

        tracker.recordStep();
        tracker.recordStep();
        assertTrue(tracker.evaluate(budget).withinBudget());

        tracker.recordStep(); // 3 steps used == maxSteps
        BudgetStatus status = tracker.evaluate(budget);
        assertFalse(status.withinBudget());
        assertTrue(status.exhaustionReason().isPresent());
        assertTrue(status.exhaustionReason().get().contains("Max execution steps exceeded"));
    }

    @Test
    void testExecutionControllerPauseResumeCancel() {
        ExecutionId execId = ExecutionId.generate();

        controller.pause(execId).await().indefinitely();
        assertEquals(ExecutionState.PAUSED, controller.getState(execId));

        controller.resume(execId).await().indefinitely();
        assertEquals(ExecutionState.ANALYZING, controller.getState(execId));

        controller.cancel(execId, "User requested cancel").await().indefinitely();
        assertEquals(ExecutionState.CANCELLED, controller.getState(execId));
    }

    @Test
    void testEndToEndOrchestrationLifecycle() {
        AgentExecution execution = orchestrator.run(
            "task-42",
            "Refactor authentication module",
            ExecutionBudget.standard(),
            ModelRequirements.standardLocal()
        ).await().atMost(Duration.ofSeconds(5));

        assertNotNull(execution);
        assertEquals(ExecutionState.COMPLETED, execution.state());
        assertTrue(execution.completedAt().isPresent());
    }
}
