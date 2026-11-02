package tech.kayys.cifr.engine.loop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.action.ActionResultStatus;
import tech.kayys.cifr.engine.contract.CriterionType;
import tech.kayys.cifr.engine.contract.GoalContract;
import tech.kayys.cifr.engine.contract.SuccessCriterion;
import tech.kayys.cifr.engine.lifecycle.AgentState;
import tech.kayys.cifr.engine.runtime.state.ExecutionRuntimeState;
import tech.kayys.cifr.engine.runtime.state.PlanRuntimeState;
import tech.kayys.cifr.engine.runtime.state.RuntimeState;
import tech.kayys.cifr.engine.runtime.state.TaskRuntimeState;
import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentDecisionCycleAndSchedulerTest {

    private DefaultAgentScheduler scheduler;
    private DefaultCompletionEvaluator evaluator;
    private DefaultStallDetector stallDetector;

    @BeforeEach
    void setUp() {
        scheduler = new DefaultAgentScheduler();
        evaluator = new DefaultCompletionEvaluator();
        stallDetector = new DefaultStallDetector();
    }

    @Test
    void testSchedulerAdvancement() {
        TaskRuntimeState taskState = new TaskRuntimeState(TaskId.of("task-1"), TaskStatus.RUNNING, AgentState.EXECUTE, 1L);
        RuntimeState state = new RuntimeState(taskState, PlanRuntimeState.empty(), Map.of(), Map.of(), Map.of(), Set.of());

        NextStep next = scheduler.schedule(state);
        assertEquals(NextStepType.RUN_DECISION_CYCLE, next.type());
        assertTrue(next.reason().contains("EXECUTE"));
    }

    @Test
    void testCompletionEvaluation() {
        SuccessCriterion crit = SuccessCriterion.mandatory("crit-1", CriterionType.TEST_PASS, "All tests pass");
        GoalContract contract = GoalContract.of("Fix auth", List.of(crit), List.of());

        ExecutionRuntimeState failedExec = new ExecutionRuntimeState("tx-1", "act-1", "COMPLETED", java.util.Optional.of(ActionResultStatus.FAILED), false);
        RuntimeState stateWithFailure = new RuntimeState(
            TaskRuntimeState.initial(TaskId.of("task-1")),
            PlanRuntimeState.empty(),
            Map.of(),
            Map.of(),
            Map.of("tx-1", failedExec),
            Set.of()
        );

        CompletionResult res = evaluator.evaluate(contract, stateWithFailure);
        assertFalse(res.isCompleted());
        assertTrue(res.summary().contains("Mandatory criterion not met"));
    }

    @Test
    void testStallDetectionOnRepeatedFailures() {
        Map<String, ExecutionRuntimeState> failures = Map.of(
            "tx-1", new ExecutionRuntimeState("tx-1", "act-1", "COMPLETED", java.util.Optional.of(ActionResultStatus.FAILED), false),
            "tx-2", new ExecutionRuntimeState("tx-2", "act-2", "COMPLETED", java.util.Optional.of(ActionResultStatus.FAILED), false),
            "tx-3", new ExecutionRuntimeState("tx-3", "act-3", "COMPLETED", java.util.Optional.of(ActionResultStatus.FAILED), false),
            "tx-4", new ExecutionRuntimeState("tx-4", "act-4", "COMPLETED", java.util.Optional.of(ActionResultStatus.FAILED), false),
            "tx-5", new ExecutionRuntimeState("tx-5", "act-5", "COMPLETED", java.util.Optional.of(ActionResultStatus.FAILED), false),
            "tx-6", new ExecutionRuntimeState("tx-6", "act-6", "COMPLETED", java.util.Optional.of(ActionResultStatus.FAILED), false)
        );

        RuntimeState state = new RuntimeState(TaskRuntimeState.initial(TaskId.of("task-1")), PlanRuntimeState.empty(), Map.of(), Map.of(), failures, Set.of());
        StallResult stall = stallDetector.inspect(state);

        assertTrue(stall.isStalled());
        assertTrue(stall.reason().contains("failure threshold exceeded"));
    }
}
