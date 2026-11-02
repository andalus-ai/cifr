package tech.kayys.cifr.engine.task.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.agent.CifrAgentContract;
import tech.kayys.cifr.engine.task.TaskState;
import tech.kayys.andalus.execution.turn.AgentTurn;
import tech.kayys.andalus.execution.turn.InMemoryTurnStore;
import tech.kayys.andalus.execution.turn.ObservationType;
import tech.kayys.andalus.execution.turn.TurnContext;
import tech.kayys.andalus.execution.turn.TurnId;
import tech.kayys.andalus.execution.turn.TurnInput;
import tech.kayys.andalus.execution.turn.TurnObservation;
import tech.kayys.andalus.execution.turn.TurnStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges Cifr task execution phases into canonical Andalus Agent Turns
 * as defined in the Andalus Framework operating model.
 */
@ApplicationScoped
public class CifrTurnBridge {

    private final TurnStore turnStore = new InMemoryTurnStore();
    private final Map<String, Long> executionSequences = new ConcurrentHashMap<>();

    public AgentTurn beginTurn(TaskState state, String phaseDescription) {
        String executionId = state.taskId().value();
        long sequence = executionSequences.compute(executionId, (k, v) -> v == null ? 1L : v + 1);

        TurnId turnId = TurnId.generate();
        TurnContext context = new TurnContext(
                turnId,
                executionId,
                CifrAgentContract.AGENT_ID,
                sequence,
                Map.of(
                        "phase", state.phase().name(),
                        "revision", state.revision(),
                        "goal", state.goal().description()
                )
        );

        TurnInput input = TurnInput.text(phaseDescription != null ? phaseDescription : state.phase().name());

        AgentTurn turn = new AgentTurn(
                turnId,
                executionId,
                sequence,
                context,
                input,
                new ArrayList<>(),
                null,
                null,
                Instant.now(),
                null,
                Map.of("created_phase", state.phase().name())
        );

        turnStore.append(turn);
        return turn;
    }

    public AgentTurn recordObservation(String executionId, TurnId turnId, ObservationType type, String source, Object value) {
        Optional<AgentTurn> opt = turnStore.find(executionId, turnId);
        if (opt.isEmpty()) {
            return null;
        }

        AgentTurn current = opt.get();
        List<TurnObservation> observations = new ArrayList<>(current.observations());
        observations.add(new TurnObservation(
                UUID.randomUUID().toString(),
                type,
                source,
                value,
                Instant.now(),
                Map.of()
        ));

        AgentTurn updated = new AgentTurn(
                current.id(),
                current.executionId(),
                current.sequence(),
                current.context(),
                current.input(),
                observations,
                current.decision(),
                current.outcome(),
                current.startedAt(),
                current.completedAt(),
                current.metadata()
        );

        turnStore.append(updated);
        return updated;
    }

    public AgentTurn completeTurn(String executionId, TurnId turnId, Object decision, Object outcome) {
        Optional<AgentTurn> opt = turnStore.find(executionId, turnId);
        if (opt.isEmpty()) {
            return null;
        }

        AgentTurn current = opt.get();
        AgentTurn completed = new AgentTurn(
                current.id(),
                current.executionId(),
                current.sequence(),
                current.context(),
                current.input(),
                current.observations(),
                decision,
                outcome,
                current.startedAt(),
                Instant.now(),
                current.metadata()
        );

        turnStore.append(completed);
        return completed;
    }

    public List<AgentTurn> listTurns(String executionId) {
        return turnStore.findByExecution(executionId);
    }

    public Optional<AgentTurn> latestTurn(String executionId) {
        return turnStore.latest(executionId);
    }

    public TurnStore getTurnStore() {
        return turnStore;
    }
}
