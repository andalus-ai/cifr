package tech.kayys.cifr.engine.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.cifr.engine.action.ActionResultStatus;
import tech.kayys.cifr.engine.action.ActionTarget;
import tech.kayys.cifr.engine.action.ActionType;
import tech.kayys.cifr.engine.contract.GoalContract;
import tech.kayys.cifr.engine.contract.SuccessCriterion;
import tech.kayys.cifr.engine.contract.CriterionType;
import tech.kayys.cifr.engine.lifecycle.AgentState;
import tech.kayys.cifr.engine.lifecycle.RecoveryDecision;
import tech.kayys.cifr.engine.planning.TaskStepId;
import tech.kayys.cifr.engine.runtime.event.*;
import tech.kayys.cifr.engine.runtime.recovery.DefaultRecoveryManager;
import tech.kayys.cifr.engine.runtime.recovery.FileReconciler;
import tech.kayys.cifr.engine.runtime.state.DefaultStateReducer;
import tech.kayys.cifr.engine.runtime.state.RuntimeState;
import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskStatus;
import tech.kayys.cifr.engine.task.step.StepStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StateReducerAndRecoveryTest {

    private DefaultStateReducer reducer;
    private DefaultRecoveryManager recoveryManager;
    private Path tempWorkspace;

    @BeforeEach
    public void setup() throws IOException {
        reducer = new DefaultStateReducer();
        recoveryManager = new DefaultRecoveryManager(new FileReconciler());
        tempWorkspace = Files.createTempDirectory("recovery-test-");
    }

    @AfterEach
    public void teardown() throws IOException {
        if (tempWorkspace != null && Files.exists(tempWorkspace)) {
            try (var s = Files.walk(tempWorkspace)) {
                s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    public void testEventReductionAndStateReconstruction() {
        TaskId taskId = TaskId.of("task-event-1");
        RuntimeState state = RuntimeState.empty(taskId);

        // 1. Task created
        GoalContract contract = GoalContract.of("Fix auth bug", List.of(SuccessCriterion.mandatory("c1", CriterionType.TEST_PASS, "Auth test passes")), List.of());
        RuntimeEvent e1 = RuntimeEvent.create(taskId, 1, EventType.TASK_CREATED, new TaskCreatedPayload("ws-1", contract));
        state = reducer.apply(state, e1);

        assertEquals(TaskStatus.RUNNING, state.task().status());
        assertEquals(AgentState.INTAKE, state.task().lifecycleState());

        // 2. Plan created
        RuntimeEvent e2 = RuntimeEvent.create(taskId, 2, EventType.PLAN_CREATED, new PlanCreatedPayload("plan-1", 2, List.of("Investigate", "Fix")));
        state = reducer.apply(state, e2);

        assertEquals("plan-1", state.plan().activePlanId());
        assertEquals(2, state.plan().stepCount());
        assertEquals(AgentState.PLAN, state.task().lifecycleState());

        // 3. Step started
        TaskStepId s1 = TaskStepId.of("step-1");
        RuntimeEvent e3 = RuntimeEvent.create(taskId, 3, EventType.STEP_STARTED, new StepStartedPayload(s1, "Investigate failure"));
        state = reducer.apply(state, e3);

        assertEquals(StepStatus.RUNNING, state.steps().get(s1).status());
        assertEquals(AgentState.EXECUTE, state.task().lifecycleState());

        // 4. Action proposed & authorized
        RuntimeEvent e4 = RuntimeEvent.create(taskId, 4, EventType.ACTION_PROPOSED, new ActionProposedPayload("prop-1", ActionType.MODIFY, ActionTarget.file("TokenValidator.java"), "Fix token expiry", 0.9));
        RuntimeEvent e5 = RuntimeEvent.create(taskId, 5, EventType.ACTION_AUTHORIZED, new ActionAuthorizedPayload("act-1", "prop-1", Instant.now().plusSeconds(300)));
        state = reducer.apply(state, e4);
        state = reducer.apply(state, e5);

        assertTrue(state.actions().get("prop-1").isAuthorized());
        assertEquals("act-1", state.actions().get("prop-1").authorizedActionId().orElse(null));

        // 5. Execution started and completed
        RuntimeEvent e6 = RuntimeEvent.create(taskId, 6, EventType.EXECUTION_STARTED, new ExecutionStartedPayload("tx-1", "act-1"));
        RuntimeEvent e7 = RuntimeEvent.create(taskId, 7, EventType.EXECUTION_COMPLETED, new ExecutionCompletedPayload("tx-1", ActionResultStatus.SUCCESS, "Patch applied cleanly"));
        state = reducer.apply(state, e6);
        state = reducer.apply(state, e7);

        assertEquals("COMPLETED", state.executions().get("tx-1").state());
        assertEquals(ActionResultStatus.SUCCESS, state.executions().get("tx-1").resultStatus().orElse(null));

        // 6. Evidence recorded & Step completed
        RuntimeEvent e8 = RuntimeEvent.create(taskId, 8, EventType.EVIDENCE_RECORDED, new EvidenceRecordedPayload("ev-100", tech.kayys.cifr.engine.store.EvidenceType.FILE_CONTENT, "Modified file content"));
        RuntimeEvent e9 = RuntimeEvent.create(taskId, 9, EventType.STEP_COMPLETED, new StepCompletedPayload(s1, "Investigation done"));
        state = reducer.apply(state, e8);
        state = reducer.apply(state, e9);

        assertTrue(state.evidenceIds().contains("ev-100"));
        assertEquals(StepStatus.COMPLETED, state.steps().get(s1).status());

        // 7. Task completed
        RuntimeEvent e10 = RuntimeEvent.create(taskId, 10, EventType.TASK_COMPLETED, new TaskCompletedPayload(true, "Goal contract satisfied"));
        state = reducer.apply(state, e10);

        assertEquals(TaskStatus.COMPLETED, state.task().status());
        assertEquals(AgentState.COMPLETE, state.task().lifecycleState());
        assertEquals(10L, state.task().lastSequence());
    }

    @Test
    public void testCrashRecoveryAndExecutionReconciliation() {
        TaskId taskId = TaskId.of("task-crash-1");
        RuntimeState state = RuntimeState.empty(taskId);

        // Simulate crash during execution: transaction started but never completed
        RuntimeEvent e1 = RuntimeEvent.create(taskId, 1, EventType.EXECUTION_STARTED, new ExecutionStartedPayload("tx-interrupted-1", "act-1"));
        state = reducer.apply(state, e1);

        assertEquals("RUNNING", state.executions().get("tx-interrupted-1").state());
        assertFalse(state.executions().get("tx-interrupted-1").isReconciled());

        // Inspection detects uncompleted transaction -> REPAIR needed
        RecoveryDecision decision = recoveryManager.inspect(state);
        assertEquals(RecoveryDecision.REPAIR, decision);

        // Recovery reconciles against workspace
        RuntimeState recoveredState = recoveryManager.recover(state, tempWorkspace).await().indefinitely();

        assertTrue(recoveredState.executions().get("tx-interrupted-1").isReconciled());
        assertTrue(recoveredState.executions().get("tx-interrupted-1").state().contains("RECONCILED"));
    }
}
