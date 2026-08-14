package tech.kayys.aljabr.engine.loop;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.api.agent.AgentEvent;
import tech.kayys.aljabr.api.agent.AgentPhase;
import tech.kayys.aljabr.api.agent.AljabrState;
import tech.kayys.aljabr.api.agent.CodingRequest;
import tech.kayys.aljabr.engine.gate.CodingExecutionContext;
import tech.kayys.aljabr.engine.gate.CompletionGate;
import tech.kayys.aljabr.engine.phase.ObservationContext;
import tech.kayys.aljabr.engine.phase.Observer;
import tech.kayys.aljabr.engine.phase.TaskAgent;
import tech.kayys.aljabr.engine.phase.TaskExecutionContext;
import tech.kayys.aljabr.engine.state.AgentStateMachine;
import tech.kayys.aljabr.engine.state.TransitionResult;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.execution.AgentToolExecutor;
import tech.kayys.wayang.tool.Tool;

import java.util.List;
import java.util.logging.Logger;

import tech.kayys.aljabr.engine.event.EventLedger;
import tech.kayys.aljabr.engine.gate.CodingExecutionContext;

@ApplicationScoped
public class DefaultCodingLoop implements CodingLoop {
    private static final Logger log = Logger.getLogger(DefaultCodingLoop.class.getName());

    @jakarta.inject.Inject
    AgentStateMachine stateMachine;

    @jakarta.inject.Inject
    Observer observer;

    @jakarta.inject.Inject
    TaskAgent taskAgent;

    @jakarta.inject.Inject
    CompletionGate completionGate;
    
    @jakarta.inject.Inject
    EventLedger eventLedger;

    @jakarta.inject.Inject
    tech.kayys.aljabr.engine.state.ExecutionStateStore stateStore;

    @jakarta.inject.Inject
    tech.kayys.aljabr.engine.eval.Evaluator evaluator;

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
        CodingRequest request = new CodingRequest(state.getTask().sessionId(), state.getTask().intent().name());

        switch (phase) {
            case INITIALIZING:
                // Move straight to OBSERVING
                return Uni.createFrom().item(new AgentEvent.ObservationCompleted());
                
            case OBSERVING:
                return observer.observe(request, new ObservationContext(state))
                        .map(result -> {
                            state.setRepository(result.repository() != null ? (tech.kayys.aljabr.engine.repo.RepositorySnapshot) result.repository() : null);
                            return new AgentEvent.PlanCreated(); // Skip explicit planning for now
                        });
                        
            case PLANNING:
                state.setPlan(new CodingPlan("plan-1", "Auto-generated plan for " + state.getTask().intent().name(), List.of("Execute request")));
                return Uni.createFrom().item(new AgentEvent.TaskSucceeded());
                
            case EXECUTING:
                return taskAgent.execute(state.getTask(), new TaskExecutionContext(state, executor, tools))
                        .map(result -> {
                            state.setLastTaskResult(result);
                            return new AgentEvent.VerificationCompleted(); 
                        });
                        
            case VERIFYING:
                tech.kayys.aljabr.engine.phase.TaskExecutionResult lastResult = state.getLastTaskResult();
                
                if (lastResult == null) {
                    return Uni.createFrom().item(new AgentEvent.TaskFailed());
                }
                
                return completionGate.evaluate(state.getTask(), lastResult, new CodingExecutionContext(state))
                        .map(decision -> {
                            if (decision.isComplete()) {
                                return new AgentEvent.ReviewCompleted();
                            } else {
                                if (state.getIteration() >= state.getTask().policy().maxRepairIterations()) {
                                    return new AgentEvent.TaskFailed(); // Goes to REPLANNING or FAILED depending on state machine
                                }
                                state.incrementIteration();
                                return new AgentEvent.TaskFailed(); // Causes RECOVERING/REPLANNING
                            }
                        });
                        
            case REVIEWING:
                return Uni.createFrom().item(new AgentEvent.TaskSucceeded()); // Goes to EVALUATING
                
            case EVALUATING:
                return evaluator.evaluate(state).map(result -> {
                    log.info("Evaluation Result: " + result.decision() + " - " + result.score());
                    if (result.decision() == tech.kayys.aljabr.engine.eval.EvaluationDecision.PASS || 
                        result.decision() == tech.kayys.aljabr.engine.eval.EvaluationDecision.TERMINATE) {
                        return new AgentEvent.EvaluationCompleted();
                    } else {
                        return new AgentEvent.TaskFailed(); // Causes REPLANNING
                    }
                });
                
            case RECOVERING:
            case REPLANNING:
                return Uni.createFrom().item(new AgentEvent.TaskSucceeded()); // Goes back to EXECUTING
                
            default:
                log.warning("Unhandled phase: " + phase);
                return Uni.createFrom().item(new AgentEvent.TaskFailed());
        }
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
