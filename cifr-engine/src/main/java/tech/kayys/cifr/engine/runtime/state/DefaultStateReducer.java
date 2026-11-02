package tech.kayys.cifr.engine.runtime.state;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.lifecycle.AgentState;
import tech.kayys.cifr.engine.planning.TaskStepId;
import tech.kayys.cifr.engine.runtime.event.*;
import tech.kayys.cifr.engine.task.TaskStatus;
import tech.kayys.cifr.engine.task.step.StepStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link StateReducer}.
 * Pure, deterministic, side-effect free reduction of runtime events into a materialized {@link RuntimeState}.
 */
@ApplicationScoped
public class DefaultStateReducer implements StateReducer {

    @Override
    public RuntimeState apply(RuntimeState current, RuntimeEvent event) {
        if (event == null) {
            return current;
        }

        TaskIdEventKey key = new TaskIdEventKey(event.taskId());
        RuntimeState state = current != null ? current : RuntimeState.empty(event.taskId());

        TaskRuntimeState task = state.task();
        PlanRuntimeState plan = state.plan();
        Map<TaskStepId, StepRuntimeState> steps = new HashMap<>(state.steps());
        Map<String, ActionRuntimeState> actions = new HashMap<>(state.actions());
        Map<String, ExecutionRuntimeState> executions = new HashMap<>(state.executions());
        Set<String> evidence = new HashSet<>(state.evidenceIds());

        long nextSequence = Math.max(task.lastSequence(), event.sequence());

        switch (event.type()) {
            case TASK_CREATED -> {
                task = new TaskRuntimeState(event.taskId(), TaskStatus.RUNNING, AgentState.INTAKE, nextSequence);
            }
            case PLAN_CREATED -> {
                if (event.payload() instanceof PlanCreatedPayload p) {
                    plan = new PlanRuntimeState(p.planId(), p.stepCount(), p.stepObjectives());
                    task = new TaskRuntimeState(task.taskId(), task.status(), AgentState.PLAN, nextSequence);
                }
            }
            case STEP_STARTED -> {
                if (event.payload() instanceof StepStartedPayload p) {
                    steps.put(p.stepId(), new StepRuntimeState(p.stepId(), StepStatus.RUNNING, p.objective()));
                    task = new TaskRuntimeState(task.taskId(), task.status(), AgentState.EXECUTE, nextSequence);
                }
            }
            case STEP_COMPLETED -> {
                if (event.payload() instanceof StepCompletedPayload p) {
                    StepRuntimeState existing = steps.get(p.stepId());
                    String obj = existing != null ? existing.objective() : "Completed step";
                    steps.put(p.stepId(), new StepRuntimeState(p.stepId(), StepStatus.COMPLETED, obj));
                }
            }
            case ACTION_PROPOSED -> {
                if (event.payload() instanceof ActionProposedPayload p) {
                    actions.put(p.proposalId(), ActionRuntimeState.proposed(p.proposalId(), p.actionType(), p.target()));
                }
            }
            case ACTION_AUTHORIZED -> {
                if (event.payload() instanceof ActionAuthorizedPayload p) {
                    ActionRuntimeState existing = actions.get(p.proposalId());
                    if (existing != null) {
                        actions.put(p.proposalId(), existing.authorize(p.actionId(), p.expiresAt()));
                    }
                }
            }
            case EXECUTION_STARTED -> {
                if (event.payload() instanceof ExecutionStartedPayload p) {
                    executions.put(p.transactionId(), ExecutionRuntimeState.start(p.transactionId(), p.actionId()));
                }
            }
            case EXECUTION_COMPLETED -> {
                if (event.payload() instanceof ExecutionCompletedPayload p) {
                    ExecutionRuntimeState existing = executions.get(p.transactionId());
                    if (existing != null) {
                        executions.put(p.transactionId(), existing.complete(p.status()));
                    }
                }
            }
            case EXECUTION_RECONCILED -> {
                if (event.payload() instanceof ExecutionReconciledPayload p) {
                    ExecutionRuntimeState existing = executions.get(p.transactionId());
                    if (existing != null) {
                        executions.put(p.transactionId(), existing.reconcile(p.realityStatus()));
                    }
                }
            }
            case EVIDENCE_RECORDED -> {
                if (event.payload() instanceof EvidenceRecordedPayload p) {
                    evidence.add(p.evidenceId());
                }
            }
            case VERIFICATION_COMPLETED -> {
                task = new TaskRuntimeState(task.taskId(), task.status(), AgentState.VERIFY, nextSequence);
            }
            case TASK_COMPLETED -> {
                task = new TaskRuntimeState(task.taskId(), TaskStatus.COMPLETED, AgentState.COMPLETE, nextSequence);
            }
            case TASK_FAILED -> {
                task = new TaskRuntimeState(task.taskId(), TaskStatus.FAILED, AgentState.FAILED, nextSequence);
            }
            case TASK_CANCELLED -> {
                task = new TaskRuntimeState(task.taskId(), TaskStatus.CANCELLED, AgentState.CANCELLED, nextSequence);
            }
            default -> {
                task = new TaskRuntimeState(task.taskId(), task.status(), task.lifecycleState(), nextSequence);
            }
        }

        return new RuntimeState(task, plan, steps, actions, executions, evidence);
    }

    private record TaskIdEventKey(tech.kayys.cifr.engine.task.TaskId id) {}
}
