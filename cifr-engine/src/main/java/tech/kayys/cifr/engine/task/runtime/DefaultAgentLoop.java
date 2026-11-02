package tech.kayys.cifr.engine.task.runtime;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.task.TaskGoal;
import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskPhase;
import tech.kayys.cifr.engine.task.TaskProgress;
import tech.kayys.cifr.engine.task.TaskResult;
import tech.kayys.cifr.engine.task.TaskState;
import tech.kayys.cifr.engine.task.TaskStatus;
import tech.kayys.cifr.engine.task.checkpoint.Checkpoint;
import tech.kayys.cifr.engine.task.checkpoint.CheckpointPolicy;
import tech.kayys.cifr.engine.task.checkpoint.CheckpointStore;
import tech.kayys.cifr.engine.task.event.TaskEvent;
import tech.kayys.cifr.engine.task.event.TaskEventStore;
import tech.kayys.cifr.engine.task.event.TaskEventType;
import tech.kayys.cifr.engine.task.snapshot.StateSnapshot;

import java.util.Map;

@ApplicationScoped
public class DefaultAgentLoop implements AgentLoop {

    private static final Logger LOG = Logger.getLogger(DefaultAgentLoop.class);

    @Inject
    CheckpointStore checkpointStore;

    @Inject
    TaskEventStore eventStore;

    @Inject
    TransitionPolicy transitionPolicy;

    @Inject
    CifrTurnBridge turnBridge;

    @Override
    public Uni<TaskResult> run(TaskId taskId) {
        return checkpointStore.latest(taskId)
            .chain(opt -> {
                TaskState state = opt
                    .map(cp -> cp.snapshot().state())
                    .orElse(new TaskState(
                        taskId,
                        TaskStatus.RUNNING,
                        TaskPhase.CREATED,
                        0L,
                        TaskGoal.of("Agent task"),
                        TaskProgress.initial()
                    ));
                LOG.infof("AgentLoop starting for task %s at phase %s", taskId.value(), state.phase());
                return executeLoop(state);
            });
    }

    private Uni<TaskResult> executeLoop(TaskState state) {
        if (state.phase() == TaskPhase.COMPLETED || state.phase() == TaskPhase.FAILED || state.phase() == TaskPhase.CANCELLED) {
            return Uni.createFrom().item(state.status() == TaskStatus.COMPLETED
                ? TaskResult.success(state.taskId(), "Task completed at phase " + state.phase())
                : TaskResult.failure(state.taskId(), "Task ended at phase " + state.phase()));
        }

        var turn = turnBridge.beginTurn(state, "Executing phase " + state.phase());
        TaskEvent event = TaskEvent.of(state.taskId(), TaskEventType.PHASE_CHANGED, Map.of("phase", state.phase().name()));
        return eventStore.append(event)
            .chain(() -> {
                TransitionDecision decision = transitionPolicy.evaluate(state, new AgentSignal.PhaseDone(state.phase().name()));
                turnBridge.completeTurn(state.taskId().value(), turn.id(), decision.nextPhase().name(), "Phase completed");
                TaskState next = state.withPhase(decision.nextPhase()).withStatus(TaskStatus.RUNNING);
                StateSnapshot snapshot = StateSnapshot.of(next);
                Checkpoint cp = Checkpoint.of(next.taskId(), next.revision(), next.phase(), snapshot);
                return checkpointStore.save(cp).chain(() -> executeLoop(next));
            });
    }
}
