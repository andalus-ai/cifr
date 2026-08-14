package tech.kayys.aljabr.engine.state;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.api.agent.AgentEvent;
import tech.kayys.aljabr.api.agent.AgentPhase;
import tech.kayys.aljabr.api.agent.AljabrState;

@ApplicationScoped
public class DefaultAgentStateMachine implements AgentStateMachine {

    @Override
    public TransitionResult transition(AljabrState state, AgentEvent event) {
        AgentPhase currentPhase = state.phase();
        AgentPhase nextPhase = determineNextPhase(currentPhase, event);

        if (nextPhase == null) {
            return new TransitionResult(state, false, "Invalid transition from " + currentPhase + " on event " + event.getClass().getSimpleName());
        }

        AljabrState nextState = new AljabrState(
            state.executionId(),
            nextPhase,
            state.plan(),
            state.progress(),
            state.workspaceRevision(),
            state.outcome()
        );

        return new TransitionResult(nextState, true, "Transitioned to " + nextPhase);
    }

    private AgentPhase determineNextPhase(AgentPhase current, AgentEvent event) {
        // Handle global cancel
        if (event instanceof AgentEvent.CancellationRequested) {
            return AgentPhase.CANCELLED;
        }

        return switch (current) {
            case INITIALIZING -> {
                if (event instanceof AgentEvent.ObservationCompleted) yield AgentPhase.OBSERVING;
                yield null;
            }
            case OBSERVING -> {
                if (event instanceof AgentEvent.PlanCreated) yield AgentPhase.PLANNING;
                yield null;
            }
            case PLANNING -> {
                if (event instanceof AgentEvent.TaskSucceeded) yield AgentPhase.EXECUTING; // or similar
                yield null;
            }
            case EXECUTING -> {
                if (event instanceof AgentEvent.VerificationCompleted) yield AgentPhase.VERIFYING;
                if (event instanceof AgentEvent.TaskFailed) yield AgentPhase.RECOVERING;
                yield null;
            }
            case VERIFYING -> {
                if (event instanceof AgentEvent.ReviewCompleted) yield AgentPhase.REVIEWING;
                if (event instanceof AgentEvent.TaskFailed) yield AgentPhase.REPLANNING;
                yield null;
            }
            case REVIEWING -> {
                if (event instanceof AgentEvent.TaskSucceeded) yield AgentPhase.EVALUATING;
                yield null;
            }
            case EVALUATING -> {
                if (event instanceof AgentEvent.EvaluationCompleted) yield AgentPhase.COMPLETED;
                if (event instanceof AgentEvent.TaskFailed) yield AgentPhase.REPLANNING;
                yield null;
            }
            case RECOVERING -> {
                if (event instanceof AgentEvent.PlanCreated) yield AgentPhase.REPLANNING;
                yield null;
            }
            case REPLANNING -> {
                if (event instanceof AgentEvent.TaskSucceeded) yield AgentPhase.EXECUTING;
                yield null;
            }
            default -> null;
        };
    }
}
