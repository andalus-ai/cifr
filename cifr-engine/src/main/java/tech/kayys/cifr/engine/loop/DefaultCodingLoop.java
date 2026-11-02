package tech.kayys.cifr.engine.loop;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.cifr.api.agent.AgentEvent;
import tech.kayys.cifr.api.agent.AgentPhase;
import tech.kayys.cifr.api.agent.CifrState;
import tech.kayys.cifr.engine.event.EventLedger;
import tech.kayys.cifr.engine.phase.PhaseHandler;
import tech.kayys.cifr.engine.state.AgentStateMachine;
import tech.kayys.cifr.engine.state.ExecutionStateStore;
import tech.kayys.cifr.engine.state.TransitionResult;
import tech.kayys.andalus.agent.AgentResponse;
import tech.kayys.andalus.execution.AgentToolExecutor;
import tech.kayys.andalus.tool.Tool;

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
        CifrState initialState = new CifrState(
                task.sessionId(),
                AgentPhase.INITIALIZING,
                null, null, null, null
        );
        codingState.setCifrState(initialState);

        // Start recursive loop
        return runPhaseLoop(codingState, executor, tools)
                .map(this::finalizeLoop);
    }

    private Uni<CodingState> runPhaseLoop(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
        CifrState currentCifrState = state.getCifrState();
        log.info("Current Phase: " + currentCifrState.phase());
        
        eventLedger.record(new AgentEvent.PhaseStarted(
                state.getTask().sessionId(), 
                currentCifrState.phase(), 
                java.time.Instant.now()
        ));

        return executeCurrentPhase(state, executor, tools)
                .onItem().invoke(event -> {
                    if (event instanceof AgentEvent.TaskFailed) {
                        eventLedger.record(new AgentEvent.PhaseFailed(
                                state.getTask().sessionId(), 
                                currentCifrState.phase(), 
                                java.time.Instant.now(), 
                                "TaskFailed event"
                        ));
                    } else {
                        eventLedger.record(new AgentEvent.PhaseCompleted(
                                state.getTask().sessionId(), 
                                currentCifrState.phase(), 
                                java.time.Instant.now()
                        ));
                    }
                })
                .onFailure().invoke(err -> {
                    eventLedger.record(new AgentEvent.PhaseFailed(
                            state.getTask().sessionId(), 
                            currentCifrState.phase(), 
                            java.time.Instant.now(), 
                            err.getMessage()
                    ));
                })
                .flatMap(event -> {
                    TransitionResult transition = stateMachine.transition(state.getCifrState(), event);
                    if (!transition.isValid()) {
                        log.severe("State machine transition invalid: " + transition.reason());
                        stateStore.fail(state.getTask().sessionId(), new RuntimeException(transition.reason()));
                        return Uni.createFrom().item(state);
                    }
                    
                    state.setCifrState(transition.nextState());
                    
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
        AgentPhase phase = state.getCifrState().phase();

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
        log.info("CodingLoop Phase: Finalize - Terminal Phase Reached: " + state.getCifrState().phase());
        
        return AgentResponse.builder()
                .content("Task finished with status: " + state.getCifrState().phase() + 
                         ". Intent: " + state.getTask().intent().name() + 
                         ". Iterations: " + state.getIteration())
                .metadata("intent", state.getTask().intent().name())
                .metadata("finalPhase", state.getCifrState().phase().name())
                .build();
    }
}
