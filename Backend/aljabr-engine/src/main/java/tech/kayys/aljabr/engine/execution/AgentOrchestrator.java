package tech.kayys.aljabr.engine.execution;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.capability.CapabilityAuthorizer;
import tech.kayys.aljabr.engine.capability.CapabilityLevel;
import tech.kayys.aljabr.engine.capability.DefaultProgressiveToolResolver;
import tech.kayys.aljabr.engine.memory.ConsolidationReport;
import tech.kayys.aljabr.engine.memory.MemoryConsolidator;
import tech.kayys.aljabr.engine.model.ModelRequirements;
import tech.kayys.aljabr.engine.router.ResilientModelRouter;
import tech.kayys.aljabr.engine.store.MemoryScope;

import java.util.List;
import java.util.Map;

/**
 * Master cognitive orchestrator coordinating the deterministic agent execution loop,
 * progressive capability disclosure, multi-tier inference routing, and memory consolidation.
 */
@ApplicationScoped
public class AgentOrchestrator {

    private static final Logger LOG = Logger.getLogger(AgentOrchestrator.class);

    @Inject
    public DefaultExecutionStateMachine stateMachine;

    @Inject
    public ResilientModelRouter modelRouter;

    @Inject
    public DefaultProgressiveToolResolver toolResolver;

    @Inject
    public CapabilityAuthorizer capabilityAuthorizer;

    @Inject
    public MemoryConsolidator memoryConsolidator;

    public AgentOrchestrator() {}

    public AgentOrchestrator(
        DefaultExecutionStateMachine stateMachine,
        ResilientModelRouter modelRouter,
        DefaultProgressiveToolResolver toolResolver,
        CapabilityAuthorizer capabilityAuthorizer,
        MemoryConsolidator memoryConsolidator
    ) {
        this.stateMachine = stateMachine;
        this.modelRouter = modelRouter;
        this.toolResolver = toolResolver;
        this.capabilityAuthorizer = capabilityAuthorizer;
        this.memoryConsolidator = memoryConsolidator;
    }

    /**
     * Executes an agent task through the deterministic state machine lifecycle until completion.
     */
    public Uni<AgentExecution> run(String taskId, String goal, ExecutionBudget budget, ModelRequirements requirements) {
        LOG.infof("AgentOrchestrator launching execution for task [%s]: %s", taskId, goal);

        AgentExecution execution = AgentExecution.create(taskId, Map.of("goal", goal));
        ExecutionBudget effectiveBudget = budget != null ? budget : ExecutionBudget.standard();
        DefaultExecutionBudgetTracker tracker = new DefaultExecutionBudgetTracker();

        // 1. Resolve inference tier before executing
        ResilientModelRouter.RoutingDecision routing = modelRouter != null
            ? modelRouter.route(requirements)
            : ResilientModelRouter.RoutingDecision.direct(ResilientModelRouter.InferenceTier.LOCAL_PRIMARY_METAL_CUDA, "Default");
        LOG.infof("Task [%s] routed to tier [%s], escalated: %s (%s)",
            taskId, routing.tier().name(), routing.escalated(), routing.explanation());

        ExecutionContext initialContext = new ExecutionContext(
            execution.id(),
            taskId,
            goal,
            ExecutionState.CREATED,
            effectiveBudget,
            Map.of("tier", routing.tier().name())
        );

        return executeLoop(initialContext, execution, tracker)
            .chain(finalExecution -> {
                // Background consolidation after task completion
                if (finalExecution.state() == ExecutionState.COMPLETED && memoryConsolidator != null) {
                    return memoryConsolidator.consolidate(taskId, MemoryScope.TASK)
                        .map(report -> {
                            LOG.infof("Post-execution memory consolidation completed for [%s]: %d facts promoted",
                                taskId, report.consolidatedMemoryIds().size());
                            return finalExecution;
                        });
                }
                return Uni.createFrom().item(finalExecution);
            });
    }

    private Uni<AgentExecution> executeLoop(ExecutionContext ctx, AgentExecution exec, ExecutionBudgetTracker tracker) {
        if (ctx.currentState().isTerminal()) {
            return Uni.createFrom().item(exec.withState(ctx.currentState()));
        }

        if (stateMachine == null) {
            return Uni.createFrom().item(exec.withState(ExecutionState.COMPLETED));
        }

        return stateMachine.step(ctx, tracker)
            .chain(nextState -> {
                AgentExecution updated = exec.withState(nextState);
                if (nextState.isTerminal()) {
                    return Uni.createFrom().item(updated);
                }
                ExecutionContext nextCtx = ctx.withState(nextState);
                return executeLoop(nextCtx, updated, tracker);
            });
    }
}
