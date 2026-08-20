package tech.kayys.aljabr.engine.loop;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.aljabr.api.agent.AgentEvent;
import tech.kayys.aljabr.api.agent.AgentPhase;
import tech.kayys.aljabr.api.agent.AljabrState;
import tech.kayys.aljabr.engine.event.EventLedger;
import tech.kayys.aljabr.engine.phase.PhaseHandler;
import tech.kayys.aljabr.engine.state.AgentStateMachine;
import tech.kayys.aljabr.engine.state.ExecutionStateStore;
import tech.kayys.aljabr.engine.state.TransitionResult;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.execution.AgentToolExecutor;
import tech.kayys.wayang.tool.Tool;

import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultCodingLoop implements CodingLoop {
    private static final Logger log = Logger.getLogger(DefaultCodingLoop.class.getName());

    @Inject
    AgentStateMachine stateMachine;

    @Inject
    Instance<PhaseHandler> phaseHandlers;

    @Inject
    EventLedger eventLedger;

    @Inject
    ExecutionStateStore stateStore;

    @Override
    public Uni<AgentResponse> execute(CodingTask task, AgentToolExecutor executor, List<Tool> tools) {
        log.info("Starting CodingLoop via AgentStateMachine for task: " + task.intent());

        CodingState codingState = new CodingState(task);
        
        // Initial state
        AljabrState initialState = new AljabrState(
                task.sessionId(),
                AgentPhase.INITIALIZING,
                null, null, null, null
        );
        codingState.setAljabrState(initialState);

        // Start recursive loop
        return runPhaseLoop(codingState, executor, tools)
                .map(this::finalizeLoop);
    }

    private Uni<CodingState> runPhaseLoop(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
        AljabrState currentAljabrState = state.getAljabrState();
        log.info("Current Phase: " + currentAljabrState.phase());
        
        eventLedger.record(new AgentEvent.PhaseStarted(
                state.getTask().sessionId(), 
                currentAljabrState.phase(), 
                java.time.Instant.now()
        ));

        return executeCurrentPhase(state, executor, tools)
                .onItem().invoke(event -> {
                    if (event instanceof AgentEvent.TaskFailed) {
                        eventLedger.record(new AgentEvent.PhaseFailed(
                                state.getTask().sessionId(), 
                                currentAljabrState.phase(), 
                                java.time.Instant.now(), 
                                "TaskFailed event"
                        ));
                    } else {
                        eventLedger.record(new AgentEvent.PhaseCompleted(
                                state.getTask().sessionId(), 
                                currentAljabrState.phase(), 
                                java.time.Instant.now()
                        ));
                    }
                })
                .onFailure().invoke(err -> {
                    eventLedger.record(new AgentEvent.PhaseFailed(
                            state.getTask().sessionId(), 
                            currentAljabrState.phase(), 
                            java.time.Instant.now(), 
                            err.getMessage()
                    ));
                })
                .flatMap(event -> {
                    TransitionResult transition = stateMachine.transition(state.getAljabrState(), event);
                    if (!transition.isValid()) {
                        log.severe("State machine transition invalid: " + transition.reason());
                        stateStore.fail(state.getTask().sessionId(), new RuntimeException(transition.reason()));
                        return Uni.createFrom().item(state);
                    }
                    
                    state.setAljabrState(transition.nextState());
                    
                    // Explicitly persist the durable execution state transition
                    stateStore.transition(
                            state.getTask().sessionId(), 
                            transition.nextState().phase(), 
                            java.util.Map.of("eventId", event.getClass().getSimpleName())
                    );
                    
                    if (isTerminalPhase(transition.nextState().phase())) {
                        stateStore.complete(state.getTask().sessionId(), null);
                        return Uni.createFrom().item(state);
                    }
                    
                    return runPhaseLoop(state, executor, tools);
                });
    }
    
    private boolean isTerminalPhase(AgentPhase phase) {
        return phase == AgentPhase.COMPLETED || 
               phase == AgentPhase.FAILED || 
               phase == AgentPhase.CANCELLED;
    }

    private Uni<AgentEvent> executeCurrentPhase(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
        AgentPhase phase = state.getAljabrState().phase();

        if (phaseHandlers != null) {
            for (PhaseHandler handler : phaseHandlers) {
                if (handler.supports(phase)) {
                    return handler.handle(state, executor, tools);
                }
            }
        }

        log.warning("Unhandled phase or no PhaseHandler registered for: " + phase);
        return Uni.createFrom().item(new AgentEvent.TaskFailed());
    }

    private AgentResponse finalizeLoop(CodingState state) {
        log.info("CodingLoop Phase: Finalize - Terminal Phase Reached: " + state.getAljabrState().phase());
        
        return AgentResponse.builder()
                .content("Task finished with status: " + state.getAljabrState().phase() + 
                         ". Intent: " + state.getTask().intent().name() + 
                         ". Iterations: " + state.getIteration())
                .metadata("intent", state.getTask().intent().name())
                .metadata("finalPhase", state.getAljabrState().phase().name())
                .build();
    }
}
