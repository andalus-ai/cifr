package tech.kayys.aljabr.engine.store;

import io.smallrye.mutiny.Uni;
import java.util.Optional;

/**
 * Temporary store for model-facing context projections.
 */
public interface ContextStore {

    Uni<Void> save(ContextSnapshot snapshot);

    Uni<Optional<ContextSnapshot>> get(String contextId);
}
