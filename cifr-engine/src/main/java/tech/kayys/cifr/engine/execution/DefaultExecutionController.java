package tech.kayys.cifr.engine.execution;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory execution controller managing execution lifecycle signals.
 */
@ApplicationScoped
public class DefaultExecutionController implements ExecutionController {

    private static final Logger LOG = Logger.getLogger(DefaultExecutionController.class);

    private final Map<String, ExecutionState> stateStore = new ConcurrentHashMap<>();
    private final Map<String, String> pendingApprovals = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> pause(ExecutionId id) {
        LOG.infof("Pausing execution [%s]", id.value());
        stateStore.put(id.value(), ExecutionState.PAUSED);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> resume(ExecutionId id) {
        LOG.infof("Resuming execution [%s]", id.value());
        stateStore.put(id.value(), ExecutionState.ANALYZING);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> cancel(ExecutionId id, String reason) {
        LOG.infof("Cancelling execution [%s]: %s", id.value(), reason);
        stateStore.put(id.value(), ExecutionState.CANCELLED);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> approve(ExecutionId id, String actionId) {
        LOG.infof("Action [%s] approved for execution [%s]", actionId, id.value());
        pendingApprovals.put(actionId, "APPROVED");
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> deny(ExecutionId id, String actionId, String reason) {
        LOG.infof("Action [%s] denied for execution [%s]: %s", actionId, id.value(), reason);
        pendingApprovals.put(actionId, "DENIED: " + reason);
        return Uni.createFrom().voidItem();
    }

    public ExecutionState getState(ExecutionId id) {
        return stateStore.getOrDefault(id.value(), ExecutionState.CREATED);
    }
}
