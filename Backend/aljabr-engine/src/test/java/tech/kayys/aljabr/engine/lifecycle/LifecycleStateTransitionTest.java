package tech.kayys.aljabr.engine.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.aljabr.engine.task.TaskId;
import tech.kayys.aljabr.engine.task.TaskGoal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LifecycleStateTransitionTest {

    private DefaultStateTransitionPolicy policy;
    private AgentTask task;

    @BeforeEach
    public void setup() {
        policy = new DefaultStateTransitionPolicy();
        task = AgentTask.create(TaskId.of("task-test-1"), "ws-1", TaskGoal.of("Fix bug"));
    }

    @Test
    public void testHappyPathTransitions() {
        // INTAKE -> CLASSIFY
        AgentState s1 = policy.evaluate(AgentState.INTAKE, StateResult.success("intake complete"), task);
        assertEquals(AgentState.CLASSIFY, s1);

        // CLASSIFY -> UNDERSTAND
        AgentState s2 = policy.evaluate(AgentState.CLASSIFY, StateResult.success("classified"), task);
        assertEquals(AgentState.UNDERSTAND, s2);

        // UNDERSTAND -> PLAN
        AgentState s3 = policy.evaluate(AgentState.UNDERSTAND, StateResult.success("understood"), task);
        assertEquals(AgentState.PLAN, s3);

        // PLAN -> EXECUTE
        AgentState s4 = policy.evaluate(AgentState.PLAN, StateResult.success("plan ready"), task);
        assertEquals(AgentState.EXECUTE, s4);

        // EXECUTE -> OBSERVE
        AgentState s5 = policy.evaluate(AgentState.EXECUTE, StateResult.success("executed"), task);
        assertEquals(AgentState.OBSERVE, s5);

        // OBSERVE -> VERIFY
        AgentState s6 = policy.evaluate(AgentState.OBSERVE, StateResult.success("observed"), task);
        assertEquals(AgentState.VERIFY, s6);

        // VERIFY (success) -> COMPLETE
        AgentState s7 = policy.evaluate(AgentState.VERIFY, StateResult.success("all tests pass"), task);
        assertEquals(AgentState.COMPLETE, s7);
        assertTrue(s7.isTerminal());
    }

    @Test
    public void testVerificationFailureAndRepairLoop() {
        // UNDERSTAND needs retrieval -> RETRIEVE
        AgentState s1 = policy.evaluate(AgentState.UNDERSTAND, StateResult.outcome(StateOutcome.NEEDS_RETRIEVAL, "more info needed"), task);
        assertEquals(AgentState.RETRIEVE, s1);

        // RETRIEVE -> PLAN
        AgentState s2 = policy.evaluate(AgentState.RETRIEVE, StateResult.success("evidence gathered"), task);
        assertEquals(AgentState.PLAN, s2);

        // VERIFY with retryable failure -> REPAIR
        AgentState s3 = policy.evaluate(AgentState.VERIFY, StateResult.outcome(StateOutcome.RETRYABLE_FAILURE, "test assertion failed"), task);
        assertEquals(AgentState.REPAIR, s3);

        // REPAIR -> RETRIEVE
        AgentState s4 = policy.evaluate(AgentState.REPAIR, StateResult.success("repair planned"), task);
        assertEquals(AgentState.RETRIEVE, s4);
    }

    @Test
    public void testTerminalFailure() {
        AgentState s = policy.evaluate(AgentState.VERIFY, StateResult.outcome(StateOutcome.TERMINAL_FAILURE, "unrecoverable syntax corruption"), task);
        assertEquals(AgentState.FAILED, s);
        assertTrue(s.isTerminal());
    }
}
