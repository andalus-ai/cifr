package tech.kayys.cifr.engine.state;

import org.junit.jupiter.api.Test;
import tech.kayys.cifr.api.agent.AgentEvent;
import tech.kayys.cifr.api.agent.AgentPhase;
import tech.kayys.cifr.api.agent.CifrState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAgentStateMachineTest {

    private final DefaultAgentStateMachine stateMachine = new DefaultAgentStateMachine();

    @Test
    void exhaustedRepairBudgetTransitionsToFailed() {
        CifrState verifying = new CifrState("execution-1", AgentPhase.VERIFYING, null, null, null, null);

        TransitionResult result = stateMachine.transition(verifying, new AgentEvent.TaskExhausted());

        assertTrue(result.isValid());
        assertEquals(AgentPhase.FAILED, result.nextState().phase());
    }

    @Test
    void recoveringTransitionsThroughReplanningBackToExecution() {
        CifrState recovering = new CifrState("execution-1", AgentPhase.RECOVERING, null, null, null, null);

        TransitionResult replanned = stateMachine.transition(recovering, new AgentEvent.PlanCreated());
        assertTrue(replanned.isValid());
        assertEquals(AgentPhase.REPLANNING, replanned.nextState().phase());

        TransitionResult executing = stateMachine.transition(replanned.nextState(), new AgentEvent.PlanCreated());
        assertTrue(executing.isValid());
        assertEquals(AgentPhase.EXECUTING, executing.nextState().phase());
    }
}
