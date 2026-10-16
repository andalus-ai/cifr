package tech.kayys.aljabr.engine.phase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.api.agent.AgentEvent;
import tech.kayys.aljabr.api.agent.AgentPhase;
import tech.kayys.aljabr.api.agent.CodingRequest;
import tech.kayys.aljabr.engine.eval.EvaluationDecision;
import tech.kayys.aljabr.engine.eval.Evaluator;
import tech.kayys.aljabr.engine.gate.CodingExecutionContext;
import tech.kayys.aljabr.engine.gate.CompletionGate;
import tech.kayys.aljabr.engine.loop.CodingPlan;
import tech.kayys.aljabr.engine.loop.CodingState;
import tech.kayys.andalus.execution.AgentToolExecutor;
import tech.kayys.andalus.tool.Tool;

import java.util.List;
import java.util.logging.Logger;

public final class DefaultPhaseHandlers {

    @ApplicationScoped
    public static class InitializingHandler implements PhaseHandler {
        @Override
        public boolean supports(AgentPhase phase) {
            return phase == AgentPhase.INITIALIZING;
        }

        @Override
        public Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
            return Uni.createFrom().item(new AgentEvent.ObservationCompleted());
        }
    }

    @ApplicationScoped
    public static class ObservingHandler implements PhaseHandler {
        @Inject
        Observer observer;

        @Override
        public boolean supports(AgentPhase phase) {
            return phase == AgentPhase.OBSERVING;
        }

        @Override
        public Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
            CodingRequest request = new CodingRequest(state.getTask().sessionId(), state.getTask().intent().name());
            return observer.observe(request, new ObservationContext(state))
                    .map(result -> {
                        state.setRepository(result.repository() != null ? (tech.kayys.aljabr.engine.repo.RepositorySnapshot) result.repository() : null);
                        return new AgentEvent.PlanCreated();
                    });
        }
    }

    @ApplicationScoped
    public static class PlanningHandler implements PhaseHandler {
        @Override
        public boolean supports(AgentPhase phase) {
            return phase == AgentPhase.PLANNING;
        }

        @Override
        public Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
            state.setPlan(new CodingPlan("plan-1", "Auto-generated plan for " + state.getTask().intent().name(), List.of("Execute request")));
            return Uni.createFrom().item(new AgentEvent.TaskSucceeded());
        }
    }

    @ApplicationScoped
    public static class ExecutingHandler implements PhaseHandler {
        @Inject
        TaskAgent taskAgent;

        @Override
        public boolean supports(AgentPhase phase) {
            return phase == AgentPhase.EXECUTING;
        }

        @Override
        public Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
            return taskAgent.execute(state.getTask(), new TaskExecutionContext(state, executor, tools))
                    .map(result -> {
                        state.setLastTaskResult(result);
                        return new AgentEvent.VerificationCompleted();
                    });
        }
    }

    @ApplicationScoped
    public static class VerifyingHandler implements PhaseHandler {
        @Inject
        CompletionGate completionGate;

        @Override
        public boolean supports(AgentPhase phase) {
            return phase == AgentPhase.VERIFYING;
        }

        @Override
        public Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
            TaskExecutionResult lastResult = state.getLastTaskResult();
            if (lastResult == null) {
                return Uni.createFrom().item(new AgentEvent.TaskFailed());
            }

            return completionGate.evaluate(state.getTask(), lastResult, new CodingExecutionContext(state))
                    .map(decision -> {
                        if (decision.isComplete()) {
                            return new AgentEvent.ReviewCompleted();
                        } else {
                            if (state.getIteration() >= state.getTask().policy().maxRepairIterations()) {
                                return new AgentEvent.TaskFailed();
                            }
                            state.incrementIteration();
                            return new AgentEvent.TaskFailed();
                        }
                    });
        }
    }

    @ApplicationScoped
    public static class ReviewingHandler implements PhaseHandler {
        @Override
        public boolean supports(AgentPhase phase) {
            return phase == AgentPhase.REVIEWING;
        }

        @Override
        public Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
            return Uni.createFrom().item(new AgentEvent.TaskSucceeded());
        }
    }

    @ApplicationScoped
    public static class EvaluatingHandler implements PhaseHandler {
        private static final Logger log = Logger.getLogger(EvaluatingHandler.class.getName());

        @Inject
        Evaluator evaluator;

        @Override
        public boolean supports(AgentPhase phase) {
            return phase == AgentPhase.EVALUATING;
        }

        @Override
        public Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
            return evaluator.evaluate(state).map(result -> {
                log.info("Evaluation Result: " + result.decision() + " - " + result.score());
                if (result.decision() == EvaluationDecision.PASS ||
                    result.decision() == EvaluationDecision.TERMINATE) {
                    return new AgentEvent.EvaluationCompleted();
                } else {
                    return new AgentEvent.TaskFailed();
                }
            });
        }
    }

    @ApplicationScoped
    public static class RecoveringHandler implements PhaseHandler {
        @Override
        public boolean supports(AgentPhase phase) {
            return phase == AgentPhase.RECOVERING || phase == AgentPhase.REPLANNING;
        }

        @Override
        public Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools) {
            return Uni.createFrom().item(new AgentEvent.TaskSucceeded());
        }
    }
}
