package tech.kayys.cifr.engine.runtime.recovery;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.lifecycle.RecoveryDecision;
import tech.kayys.cifr.engine.runtime.state.RuntimeState;

import java.nio.file.Path;

/**
 * Manages post-crash runtime inspection, reconciliation of interrupted transactions, and safe resumption.
 */
public interface RecoveryManager {

    RecoveryDecision inspect(RuntimeState state);

    Uni<RuntimeState> recover(RuntimeState state, Path workspaceRoot);
}
